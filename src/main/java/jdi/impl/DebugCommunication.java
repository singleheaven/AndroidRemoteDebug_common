package jdi.impl;

import com.freddy.chat.bean.SingleMessage;
import com.freddy.chat.im.MessageType;
import com.freddy.chat.utils.StringUtil;
import com.freddy.im.protobuf.MessageProtobuf;
import jdi.jdwp.SocketTransportWrapper;
import jdi.jdwp.TransportWrapper;
import jdi.log.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

abstract class DebugCommunication {
    public static final String serverId = "server";
    private static final String TAG = "DebugCommunication";
    private final TransportWrapper transportWrapper = new SocketTransportWrapper();

    protected final String myId;

    public DebugCommunication(String myId) {
        this.myId = myId;
    }

    protected abstract void doSendMessageToRemote(SingleMessage message);

    protected abstract void linkToRemote();

    protected abstract void unlinkFromRemote();

    protected abstract boolean isNetworkAvailable();

    protected abstract MessageProtobuf.Msg getHandshakeMsg();

    protected MessageProtobuf.Msg getHeartbeatMsg() {
        MessageProtobuf.Msg.Builder builder = MessageProtobuf.Msg.newBuilder();
        MessageProtobuf.Head.Builder headBuilder = MessageProtobuf.Head.newBuilder();
        headBuilder.setMsgId(UUID.randomUUID().toString());
        headBuilder.setMsgType(MessageType.HEARTBEAT.getMsgType());
        headBuilder.setFromId(myId);
        headBuilder.setTimestamp(System.currentTimeMillis());
        builder.setHead(headBuilder.build());

        return builder.build();
    }

    private volatile boolean running = false;
    protected volatile String theOtherSideId = "";

    private static final int MAX_OVERTIME_IN_SECONDS = 60;

    private final Map<String, Long> queue = new ConcurrentHashMap<>();

    private void enqueueMessage(String id, long nanoTime) {
        queue.put(id, nanoTime);
    }

    private void dequeueMessage(String id) {
        queue.remove(id);
    }

    private void checkOvertime() {
        new Thread(() -> {
            while (running) {
                Set<Map.Entry<String, Long>> set = queue.entrySet();
                for (Map.Entry<String, Long> entry : set) {
                    if (TimeUnit.NANOSECONDS.toSeconds(
                            entry.getValue() - System.nanoTime())
                            > MAX_OVERTIME_IN_SECONDS) {
                        onError(entry.getKey() + " overtime");
                        stop();
                        break;
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    protected abstract void onError(String msg);

    public void start() {
        linkToRemote();

/*        SingleMessage message = new SingleMessage();
        message.setMsgType(MessageType.HANDSHAKE.getMsgType());
        message.setMsgId(UUID.randomUUID().toString());
        message.setFromId(myId);
        message.setToId(serverId);
        doSendMessageToRemote(message);*/
    }

    public void stop() {
        SingleMessage message = new SingleMessage();
        message.setMsgId(UUID.randomUUID().toString());
        message.setFromId(myId);
        message.setToId(theOtherSideId);
        message.setMsgType(MessageType.GOODBYE.getMsgType());
        doSendMessageToRemote(message);
    }

    private void doStop() {
        unlinkFromRemote();
    }

    public final void detachFromJVM() {
        running = false;
        try {
            transportWrapper.close();
        } catch (IOException e) {
            Log.e(TAG, "detachFromJVM error", e);
        }
    }

    public final void attachToJVM(String address) {
        // address = "127.0.0.1:8011";
        try {
            transportWrapper.attach(address, 0, 0);
        } catch (IOException e) {
            Log.e(TAG, "attachToJVM error", e);
        }
        running = true;
        new Thread(this::loopReceivingMessageFromJVMAndSendToRemote).start();
        checkOvertime();
    }

    // 实现一个简单的发送接收队列，并在不可靠通信的全链路引入较为严格的超时机制
    // 先保证通信的正确性，后续再结合实际做优化。
    // https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html
    // 所有jdwp消息都需要对方在规定时间内回复“已收到”，否则就断开当前debug session。
    // 服务端和接收端也无需设计消息重发机制

    private void loopReceivingMessageFromJVMAndSendToRemote() {
        while (running) {
            try {
                byte[] bytes = transportWrapper.readPacket();
                SingleMessage message = new SingleMessage();
                message.setMsgId(UUID.randomUUID().toString());
                message.setFromId(myId);
                message.setToId(theOtherSideId);
                message.setContent(bytes);
                sendMessageToRemote(message);
                Log.d(TAG, String.format("loopReceivingMessageFromJVMAndSendToRemote myId=%s, toId=%s",
                        myId, theOtherSideId));
            } catch (Exception e) {
                Log.e(TAG, "loopReceivingMessageFromJVMAndSendToRemote error", e);
                onError("loopReceivingMessageFromJVMAndSendToRemote error");
            }
        }
    }

    public final void sendMessageToRemote(SingleMessage message) {
        enqueueMessage(message.getMsgId(), System.nanoTime());
        doSendMessageToRemote(message);
    }

    private static final byte[] MSG_RECEIVED_VALUE = "~OK~".getBytes();

    protected abstract void onPeerHandshake();

    protected abstract void onPeerGoodbye();

    protected final void goodbye(String hostId) {
        assert this.theOtherSideId.equals(hostId);
        doStop();
    }

    public final void startListening(String address) throws IOException {
        transportWrapper.startListening(address);
        transportWrapper.accept(0, 0);
        running = true;
        checkOvertime();
        loopReceivingMessageFromJVMAndSendToRemote();
    }

    public final void stopListening() throws IOException {
        running = false;
        transportWrapper.stopListening();
    }

    protected final void sendMessageToJVM(@org.jetbrains.annotations.NotNull SingleMessage message) {
        byte[] bytes = message.getContent();
        String id = message.getMsgId();
        try {
            if (null != bytes) {
                if (Arrays.equals(bytes, MSG_RECEIVED_VALUE)) {
                    dequeueMessage(id);
                } else {
                    assert !StringUtil.isEmpty(theOtherSideId);

                    message.setFromId(myId);
                    message.setToId(theOtherSideId);
                    message.setContent(MSG_RECEIVED_VALUE);
                    doSendMessageToRemote(message);

                    transportWrapper.writePacket(bytes);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "sendMessageToJVM error", e);
            onError("sendMessageToJVM error");
        }
    }
}
