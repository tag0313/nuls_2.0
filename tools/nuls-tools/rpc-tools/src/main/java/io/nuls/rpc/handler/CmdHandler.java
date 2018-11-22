/*
 *
 *  * MIT License
 *  *
 *  * Copyright (c) 2017-2018 nuls.io
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *  *
 *
 */

package io.nuls.rpc.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.nuls.rpc.cmd.BaseCmd;
import io.nuls.rpc.info.Constants;
import io.nuls.rpc.info.RuntimeInfo;
import io.nuls.rpc.model.CmdDetail;
import io.nuls.rpc.model.message.*;
import io.nuls.tools.core.ioc.SpringLiteContext;
import io.nuls.tools.data.DateUtils;
import io.nuls.tools.parse.JSONUtils;
import io.nuls.tools.thread.TimeService;
import org.java_websocket.WebSocket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tangyi
 * @date 2018/10/30
 * @description
 */
public class CmdHandler {

    /**
     * Call local cmd.
     * 1. If the interface is injected via @Autowired, the injected object is used
     * 2. If the interface has no special annotations, construct a new object by reflection
     */
    public static Object invoke(String invokeClass, String invokeMethod, Map params) throws Exception {

        Class clz = Class.forName(invokeClass);
        @SuppressWarnings("unchecked") Method method = clz.getDeclaredMethod(invokeMethod, Map.class);

        BaseCmd cmd;
        if (SpringLiteContext.getBeanByClass(invokeClass) == null) {
            @SuppressWarnings("unchecked") Constructor constructor = clz.getConstructor();
            cmd = (BaseCmd) constructor.newInstance();
        } else {
            cmd = (BaseCmd) SpringLiteContext.getBeanByClass(invokeClass);
        }

        return method.invoke(cmd, params);
    }


    /**
     * Build basic message object
     */
    public static Message basicMessage(int messageId, MessageType messageType) {
        Message message = new Message();
        message.setMessageId(messageId);
        message.setMessageType(messageType.name());
        message.setTimestamp(TimeService.currentTimeMillis());
        message.setTimezone(DateUtils.getTimeZone());
        return message;
    }

    /**
     * Default NegotiateConnection object
     */
    public static NegotiateConnection defaultNegotiateConnection() {
        NegotiateConnection negotiateConnection = new NegotiateConnection();
        negotiateConnection.setCompressionAlgorithm("zlib");
        negotiateConnection.setCompressionRate(0);
        return negotiateConnection;
    }

    /**
     * Send NegotiateConnectionResponse
     */
    public static void negotiateConnectionResponse(WebSocket webSocket) throws JsonProcessingException {
        NegotiateConnectionResponse negotiateConnectionResponse = new NegotiateConnectionResponse();
        negotiateConnectionResponse.setNegotiationStatus(0);
        negotiateConnectionResponse.setNegotiationComment("Incompatible protocol version");

        Message rspMsg = basicMessage(RuntimeInfo.nextSequence(), MessageType.NegotiateConnectionResponse);
        rspMsg.setMessageData(negotiateConnectionResponse);
        webSocket.send(JSONUtils.obj2json(rspMsg));
    }

    public static void response(WebSocket webSocket, Map<String, Object> messageMap) throws Exception {
        int messageId = (Integer) messageMap.get("messageId");
        Map messageData = (Map) messageMap.get("messageData");
        Map requestMethods = (Map) messageData.get("requestMethods");
        for (Object method : requestMethods.keySet()) {
            Response response = defaultResponse(messageId);

            Map params = (Map) requestMethods.get(method);
            CmdDetail cmdDetail = params == null || params.get(Constants.VERSION_KEY_STR) == null
                    ? RuntimeInfo.getLocalInvokeCmd((String) method)
                    : RuntimeInfo.getLocalInvokeCmd((String) method, Double.parseDouble(params.get(Constants.VERSION_KEY_STR).toString()));

            response.setResponseData(cmdDetail == null
                    ? "No such version: " + method + "," + (params != null ? params.get(Constants.VERSION_KEY_STR) : "")
                    : CmdHandler.invoke(cmdDetail.getInvokeClass(), cmdDetail.getInvokeMethod(), params));

            Message message = basicMessage(RuntimeInfo.nextSequence(), MessageType.Response);
            message.setMessageData(response);
            response.setResponseProcessingTime(TimeService.currentTimeMillis() - response.getResponseProcessingTime());
            webSocket.send(JSONUtils.obj2json(message));
        }
    }

    public static void unsubscribe() {

    }

    public static Request defaultRequest() {
        Request request = new Request();
        request.setRequestAck(0);
        request.setSubscriptionEventCounter(0);
        request.setSubscriptionPeriod(0);
        request.setSubscriptionRange("");
        request.setResponseMaxSize(0);
        request.setRequestMethods(new HashMap<>(16));
        return request;
    }

    private static Response defaultResponse(int requestId) {
        Response response = new Response();
        response.setRequestId(requestId);
        response.setResponseProcessingTime(TimeService.currentTimeMillis());
        response.setResponseStatus(1);
        response.setResponseComment("Congratulations! Processing completed！");
        response.setResponseMaxSize(0);
        return response;
    }
}