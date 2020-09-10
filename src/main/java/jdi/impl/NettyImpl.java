package jdi.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.freddy.chat.bean.AppMessage;
import com.freddy.chat.bean.SingleMessage;
import com.freddy.chat.event.CEventCenter;
import com.freddy.chat.event.Events;
import com.freddy.chat.event.I_CEventListener;
import com.freddy.chat.im.IMSClientBootstrap;
import com.freddy.chat.im.IMSEventListener;
import com.freddy.chat.im.MessageProcessor;
import com.freddy.chat.utils.StringUtil;
import com.freddy.im.protobuf.MessageProtobuf;
import jdi.log.Log;

import java.util.Arrays;

public abstract class NettyImpl extends DebugCommunication {
    private static final String[] EVENTS = {
            Events.CHAT_SINGLE_MESSAGE, Events.HANDSHAKE_MESSAGE, Events.GOODBYE_MESSAGE
    };
    private static final String TAG = "NettyImpl";

    private final IMSEventListener imsEventListener;

    private final I_CEventListener eventListener = (topic, msgCode, resultCode, obj) -> {
        if (Events.CHAT_SINGLE_MESSAGE.equals(topic)) {
            final SingleMessage message = (SingleMessage) obj;
            Log.d(TAG, "onCEvent:" + message.getMsgId());
            sendMessageToJVM(message);
        } else if (Events.HANDSHAKE_MESSAGE.equals(topic)) {
            final AppMessage message = (AppMessage) obj;
            JSONObject jsonObj = JSON.parseObject(message.getHead().getExtend());
            theOtherSideId = jsonObj.getString("other_side_id");
            assert !StringUtil.isEmpty(theOtherSideId);
            onPeerHandshake();
        } else if (Events.GOODBYE_MESSAGE.equals(topic)) {
            final AppMessage message = (AppMessage) obj;
            JSONObject jsonObj = JSON.parseObject(message.getHead().getExtend());
            goodbye(jsonObj.getString("other_side_id"));
            onPeerGoodbye();
        }
    };
    private final String hosts;

    public NettyImpl(String id, String host, int port) {
        super(id);
        hosts = String.format("[{\"host\":\"%s\", \"port\":%d}]", host, port);

        imsEventListener = new IMSEventListener(id, "token_" + id) {
            @Override
            public boolean isNetworkAvailable() {
                return NettyImpl.this.isNetworkAvailable();
            }

            @Override
            public MessageProtobuf.Msg getHandshakeMsg() {
                return NettyImpl.this.getHandshakeMsg();
            }

            @Override
            public MessageProtobuf.Msg getHeartbeatMsg() {
                return NettyImpl.this.getHeartbeatMsg();
            }
        };
    }

    @Override
    protected void doSendMessageToRemote(SingleMessage message) {
        MessageProcessor.getInstance().sendMsg(message);
        Log.d(TAG, "sendMsg message: " + message + ", size="
                + (null == message.getContent() ? 0 : message.getContent().length) + ", content is:"
                + Arrays.toString(message.getContent()));
    }

    @Override
    protected void linkToRemote() {
        CEventCenter.registerEventListener(eventListener, EVENTS);
        IMSClientBootstrap.getInstance().init(imsEventListener, hosts, 1);
    }

    @Override
    protected void unlinkFromRemote() {
        CEventCenter.unregisterEventListener(eventListener, EVENTS);
    }
}
