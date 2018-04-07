package com.netty.nettyDemo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.Date;

public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private static final String WEBSOCKET_URL = "ws://localhost:8888/websocket";
    private static WebSocketServerHandshaker handshaker;

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handWebSocketRequest(ctx, (WebSocketFrame) msg);
        }
    }

    private void handHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.getDecoderResult().isSuccess() ||
                !("websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpRequest(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.BAD_REQUEST));
        }
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                WEBSOCKET_URL, null, false);
        handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private void sendHttpRequest(ChannelHandlerContext ctx, FullHttpRequest msg,
                                 DefaultFullHttpResponse defaultFullHttpResponse) {
        if (defaultFullHttpResponse.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(
                    defaultFullHttpResponse.getStatus().toString(), CharsetUtil.UTF_8);
            defaultFullHttpResponse.content().writeBytes(buf);
            buf.release();
        }
        ChannelFuture f = ctx.channel().writeAndFlush(defaultFullHttpResponse);
        if (defaultFullHttpResponse.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handWebSocketRequest(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 判断是否是关闭websocket的指令
        if (frame instanceof CloseWebSocketFrame) {
            System.out.println("close---");
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            System.out.println("ping---");
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        //判断是否是二进制消息，如果是二进制消息，抛出异常
        if (!(frame instanceof TextWebSocketFrame)) {
            System.out.println("目前我们不支持二进制消息");
            throw new RuntimeException("【" + this.getClass().getName() + "】不支持消息");
        }
        //返回应答消息
        //获取客户端向服务端发送的消息
        String request = ((TextWebSocketFrame) frame).text();
        System.out.println("服务端收到客户端的消息====>>>" + request);
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date().toString()
                + ctx.channel().id()
                + " ===>>> "
                + request);
        //群发，服务端向每个连接上来的客户端群发消息
        NettyConfig.group.writeAndFlush(tws);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.group.add(ctx.channel());
        System.out.println("客户端与服务器连接开启");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.group.remove(ctx.channel());
        System.out.println("客户端与服务器端断开连接");
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
