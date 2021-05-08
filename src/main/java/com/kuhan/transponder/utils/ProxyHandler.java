package com.kuhan.transponder.utils;

import com.google.protobuf.Message;
import com.kuhan.transponder.protocol.Echo;
import com.kuhan.transponder.protocol.standard.BaiduRpcProto;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;
@Slf4j
public class ProxyHandler<T> implements InvocationHandler {

    public ProxyHandler(){
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        BaiduRpcProto.RpcMeta.Builder metaBuilder = null;
        BaiduRpcProto.RpcRequestMeta.Builder requestMeta = null;
        byte[] realContent;
        byte[] metaContent;
        // 需要传的proto
        Echo.EchoRequest request = null;
        request = Echo.EchoRequest.newBuilder().setMessage("test").build();
        // 百度proto
        metaBuilder = BaiduRpcProto.RpcMeta.newBuilder();
        // 封装实际请求
        requestMeta = BaiduRpcProto.RpcRequestMeta.newBuilder();
        requestMeta.setServiceName("example.Echoservice");
        requestMeta.setMethodName("Echo");
        requestMeta.setLogId(0);
        metaBuilder.setRequest(requestMeta.build());
        metaBuilder.setCorrelationId(1);
        metaBuilder.setCompressType(0);
        // 实际信息，即proto
        Object parameter = args[0];
        realContent = ((Message)parameter).toByteArray();
        //realContent = request.toByteArray();
        // 百度proto
        metaContent = metaBuilder.build().toByteArray();
        ByteBuf headerBuf = Unpooled.buffer(12);
        ByteBuf metaBuf = Unpooled.wrappedBuffer(metaContent);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        EventLoopGroup workgroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap(); // 客户端
        b.group(workgroup)
                .channel(NioSocketChannel.class) // 客户端 -->NioSocketChannel
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(
                        new ChannelInitializer<SocketChannel>() { // handler
                            @Override
                            protected void initChannel(SocketChannel sc) throws Exception {
                                sc.pipeline()
                                        .addLast(
                                                new SimpleChannelInboundHandler<Object>() {
                                                    public void channelRead0(ChannelHandlerContext ctx, Object msg)
                                                            throws Exception {
                                                        try {
                                                            // ByteBuf fixHeaderBuf = Unpooled.buffer(12);
                                                            byte[] magic = new byte[4];
                                                            // PRPC魔术字段
                                                            ByteBuf buf = (ByteBuf) msg;
                                                            buf.readBytes(magic);
                                                            if (!Arrays.equals(magic, "PRPC".getBytes())) {
                                                                System.out.println("PRPC Exception !");
                                                                throw new NullPointerException();
                                                            }
                                                            // 12字节长度
                                                            int bodySize = buf.readInt();

                                                            int metaSize = buf.readInt();

                                                            ByteBuf metaBuf = buf.readBytes(metaSize);

                                                            byte[] metaBytes = new byte[metaBuf.readableBytes()];
                                                            metaBuf.readBytes(metaBytes,0,metaBuf.readableBytes());
                                                            //附件
                                                            ByteBuf protoAndAttachmentBuf = buf.readBytes(bodySize - metaSize);

                                                            BaiduRpcProto.RpcMeta rpcMeta = (BaiduRpcProto.RpcMeta) ProtocolUtils.parseFrom(metaBytes);

                                                            BaiduRpcProto.RpcResponseMeta responseMeta = rpcMeta.getResponse();
                                                            //log.info("sync response success, response=%s\n"+responseMeta.toString());
                                                            System.out.printf("sync call success, response=%s\n", responseMeta.toString());
                                                        }catch(Exception e){
                                                            e.printStackTrace();
                                                        } finally{
                                                            ReferenceCountUtil.release(msg);
                                                        }
                                                    }

                                                    @Override
                                                    public void exceptionCaught(
                                                            ChannelHandlerContext channelHandlerContext, Throwable throwable)
                                                            throws Exception {}
                                                });
                            }
                        });

        headerBuf.writeBytes("PRPC".getBytes());
        headerBuf.writeInt(realContent.length + metaContent.length);
        headerBuf.writeInt(metaContent.length);
        ByteBuf protoBuf = Unpooled.wrappedBuffer(realContent);
        ByteBuf buffer = Unpooled.wrappedBuffer(headerBuf, metaBuf, protoBuf);
        ChannelFuture future = b.connect(new InetSocketAddress("localhost", 8002)).sync();
        ChannelFuture ret = future.channel().writeAndFlush(buffer).sync();
        future.channel().read();
        System.out.printf("sync call success, response=%s\n", ret.toString());
        System.out.println("Before invoke "  + method.getName());
        Object result = method.invoke(proxy, args);
        Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage("test").build();
        System.out.println("After invoke " + method.getName());
        return response;
    }

    public static <T> T getProxy(Class clazz,ProxyHandler proxyHandler){
        return (T)Proxy.newProxyInstance(clazz.getClassLoader(),new Class[]{clazz},proxyHandler);
    }
}