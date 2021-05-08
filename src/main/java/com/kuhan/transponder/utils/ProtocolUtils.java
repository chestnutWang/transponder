package com.kuhan.transponder.utils;

import com.google.protobuf.Message;
import com.kuhan.transponder.protocol.standard.BaiduRpcProto;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class ProtocolUtils {
    private static final BaiduRpcProto.RpcMeta defaultRpcMetaInstance = BaiduRpcProto.RpcMeta.getDefaultInstance();
    public static Message parseFrom(byte[] input) throws IOException {
        return ProtocolUtils.parseFrom(input,defaultRpcMetaInstance);
    }
    public static Message parseFrom(byte[] input, Message defaultInstance) throws IOException {
        return defaultInstance.getParserForType().parseFrom(input);
    }

    public static Message parseFrom(ByteBuf input, Message defaultInstance) throws IOException {
        final int length = input.readableBytes();
        byte[] array = new byte[length];
        input.readBytes(array, 0, length);
        return defaultInstance.getParserForType().parseFrom(array);
    }
}
