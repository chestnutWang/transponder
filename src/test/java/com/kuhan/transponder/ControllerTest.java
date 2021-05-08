package com.kuhan.transponder;

import com.kuhan.transponder.protocol.Echo;
import com.kuhan.transponder.protocol.standard.BaiduRpcProto;

import com.kuhan.transponder.service.service.EchoService;
import com.kuhan.transponder.service.standard.Stream;
import com.kuhan.transponder.utils.ProtocolUtils;
import com.kuhan.transponder.utils.ProxyHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

@Slf4j
public class ControllerTest {
  Echo.EchoRequest request = null;
  BaiduRpcProto.RpcMeta.Builder metaBuilder = null;
  BaiduRpcProto.RpcRequestMeta.Builder requestMeta = null;
  byte[] realContent;
  byte[] metaContent;

  @Before
  public void before() {
    // 需要传的proto
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
    realContent = request.toByteArray();
    // 百度proto
    metaContent = metaBuilder.build().toByteArray();
    //int metaSize = metaContent.length;
    //int bodySize = realContent.length + metaSize;
  }
  /*
  4byte|4byte|4byte|RpcMeta|EcohRequest
  ”PRPC”|body length|meta length|meta|content
   */
  @Test
  public void testSocket() {
    try {
        // 需要传的proto
      Echo.EchoRequest request = Echo.EchoRequest.newBuilder().setMessage("test!!!").build();

      // 百度proto
      BaiduRpcProto.RpcMeta.Builder metaBuilder = BaiduRpcProto.RpcMeta.newBuilder();

      // 封装实际请求
      BaiduRpcProto.RpcRequestMeta.Builder requestMeta = BaiduRpcProto.RpcRequestMeta.newBuilder();
      requestMeta.setServiceName("example.Echoservice");
      requestMeta.setMethodName("Echo");
      requestMeta.setLogId(1234);
      metaBuilder.setCorrelationId(1);
      metaBuilder.setCompressType(0);
      metaBuilder.setRequest(requestMeta.build());
      // 实际信息，即proto,请求转为byte数组
      byte[] realContent = request.toByteArray();
      // 百度proto
      byte[] metaContent = metaBuilder.build().toByteArray();
      int metaSize = metaContent.length;
      int bodySize = realContent.length + metaSize;
      Socket socket = new Socket("localhost", 8002);
      OutputStream outputStream = socket.getOutputStream();

      // 写rpc头
      // OutputStream os = new BufferedOutputStream(outputStream);
      byte[] prpc = "PRPC".getBytes();
      outputStream.write("PRPC".getBytes());
      // body长度
      outputStream.write(intToByteArray(bodySize));
      // meta长度
      outputStream.write(intToByteArray(metaSize));

      // 写request
      outputStream.write(metaContent);
      outputStream.write(realContent);
      outputStream.flush();

      // 解响应
      InputStream inputStream = socket.getInputStream();
      // 读proto类型
      byte[] ret = new byte[1024];
      inputStream.read(ret);
      // 元数据长度
      int meta = byteArrayToInt(ret, 4);
      // 实际数据长度
      int body = byteArrayToInt(ret, 8);
      byte[] metaB = new byte[body];
      byte[] bodyB = new byte[meta - body];
      System.arraycopy(ret, 12, metaB, 0, body);
      System.arraycopy(ret, 12 + body, bodyB, 0, meta - body);

      BaiduRpcProto.RpcMeta rpcMeta = (BaiduRpcProto.RpcMeta) ProtocolUtils.parseFrom(metaB);
      // 解包
      // BaiduRpcProto.RpcResponseMeta responseMeta = rpcMeta.getResponse();
      Echo.EchoResponse response = Echo.EchoResponse.parseFrom(bodyB);
      System.out.printf("sync call success, response=%s\n", response.getMessage());
      inputStream.close();
      outputStream.close();
      // bufferedInputStream.close();
      socket.close();
      // Echo.EchoResponse response = restTemplate.getForObject("localhost:8080",
      // Echo.EchoResponse.class);
      // System.out.printf("sync call success, response=%s\n", response.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * int 转 byte
   *
   * @param i
   * @return
   */
  public static byte[] intToByteArray(int i) {
    byte[] result = new byte[4];
    result[0] = (byte) ((i >> 24) & 0xFF);
    result[1] = (byte) ((i >> 16) & 0xFF);
    result[2] = (byte) ((i >> 8) & 0xFF);
    result[3] = (byte) (i & 0xFF);
    return result;
  }

  public static int byteArrayToInt(byte[] bytes, int offset) {
    int value = 0;
    // 由高位到低位
    for (int i = offset; i < offset + 4; i++) {
      int shift = (4 - 1 - i) * 8;
      value += (bytes[i] & 0x000000FF) << shift; // 往高位游
    }
    return value;
  }

  @Test
  public void testNetty() {
    try {
      // 准备数据

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

                               // log.info("sync response success, response=%s\n"+responseMeta.toString());
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
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testJDKProxy(){
// 需要传的proto
    Echo.EchoRequest request = Echo.EchoRequest.newBuilder().setMessage("copy!!!").build();
    ProxyHandler proxyHandler = new ProxyHandler();
    EchoService echoService = proxyHandler.getProxy(EchoService.class,proxyHandler);
    Echo.EchoResponse response = echoService.echo(request);
    //System.out.println(String.format("sync response:",response.getMessage()));
    //Proxy.newProxyInstance(EchoService,,proxyHandler);
  }

  @Test
  public void testResource() {
    try{
    //实际请求
    Stream.CreateStreamRequest request = Stream.CreateStreamRequest.newBuilder().setName("test").setCapacity(10000).setType(Stream.StreamType.valueOf(0)).build();
    // 百度proto
    BaiduRpcProto.RpcMeta.Builder metaBuilder = BaiduRpcProto.RpcMeta.newBuilder();

    // 封装实际请求
    BaiduRpcProto.RpcRequestMeta.Builder requestMeta = BaiduRpcProto.RpcRequestMeta.newBuilder();
    requestMeta.setServiceName("StreamService");
    requestMeta.setMethodName("CreateStream");
    requestMeta.setLogId(1234);
    metaBuilder.setCorrelationId(1);
    metaBuilder.setCompressType(0);
    metaBuilder.setRequest(requestMeta.build());
    // 实际信息，即proto,请求转为byte数组
    byte[] realContent = request.toByteArray();
    // 百度proto
    byte[] metaContent = metaBuilder.build().toByteArray();
    int metaSize = metaContent.length;
    int bodySize = realContent.length + metaSize;
    Socket socket = new Socket("172.20.50.224", 8000);
    OutputStream outputStream = socket.getOutputStream();

    // 写rpc头
    // OutputStream os = new BufferedOutputStream(outputStream);
    byte[] prpc = "PRPC".getBytes();
    outputStream.write("PRPC".getBytes());
    // body长度
    outputStream.write(intToByteArray(bodySize));
    // meta长度
    outputStream.write(intToByteArray(metaSize));

    // 写request
    outputStream.write(metaContent);
    outputStream.write(realContent);
    outputStream.flush();
    // 解响应
      InputStream inputStream = socket.getInputStream();
      // 读proto类型
      byte[] ret = new byte[1024];
      inputStream.read(ret);
      // 元数据长度
      int meta = byteArrayToInt(ret, 4);
      // 实际数据长度
      int body = byteArrayToInt(ret, 8);
      byte[] metaB = new byte[body];
      byte[] bodyB = new byte[meta - body];
      System.arraycopy(ret, 12, metaB, 0, body);
      System.arraycopy(ret, 12 + body, bodyB, 0, meta - body);
      BaiduRpcProto.RpcMeta rpcMeta = (BaiduRpcProto.RpcMeta) ProtocolUtils.parseFrom(metaB);
      // 解包
      // BaiduRpcProto.RpcResponseMeta responseMeta = rpcMeta.getResponse();
      Stream.CreateStreamResponse response = Stream.CreateStreamResponse.parseFrom(bodyB);
      System.out.printf("sync call success, response=%s\n");
      inputStream.close();
      outputStream.close();
      // bufferedInputStream.close();
      socket.close();
    }catch(Exception e){
      e.printStackTrace();
    }
  }
}
