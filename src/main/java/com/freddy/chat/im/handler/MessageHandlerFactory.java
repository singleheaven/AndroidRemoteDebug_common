package com.freddy.chat.im.handler;

import com.freddy.chat.im.MessageType;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>@ProjectName:     NettyChat</p>
 * <p>@ClassName:       MessageHandlerFactory.java</p>
 * <p>@PackageName:     com.freddy.chat.im.handler</p>
 * <b>
 * <p>@Description:     消息处理handler工厂</p>
 * </b>
 * <p>@author:          FreddyChen</p>
 * <p>@date:            2019/04/10 03:44</p>
 * <p>@email:           chenshichao@outlook.com</p>
 */
public class MessageHandlerFactory {

    private MessageHandlerFactory() {

    }

    private static final Map<Integer, IMessageHandler> HANDLERS = new HashMap<>();

    static {
        /* 单聊消息处理handler */
        HANDLERS.put(MessageType.SINGLE_CHAT.getMsgType(), new SingleChatMessageHandler());
        /* 群聊消息处理handler */
        HANDLERS.put(MessageType.GROUP_CHAT.getMsgType(), new GroupChatMessageHandler());
        /* 服务端返回的消息发送状态报告处理handler */
        HANDLERS.put(MessageType.SERVER_MSG_SENT_STATUS_REPORT.getMsgType(), new ServerReportMessageHandler());
        /* 服务端返回的握手消息handler */
        HANDLERS.put(MessageType.HANDSHAKE.getMsgType(), new HandShakeMessageHandler());
        HANDLERS.put(MessageType.GOODBYE.getMsgType(), new GoodbyeMessageHandler());
    }

    /**
     * 根据消息类型获取对应的处理handler
     *
     * @param msgType
     * @return
     */
    public static IMessageHandler getHandlerByMsgType(int msgType) {
        return HANDLERS.get(msgType);
    }
}
