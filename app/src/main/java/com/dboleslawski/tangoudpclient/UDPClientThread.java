
package com.dboleslawski.tangoudpclient;
/*
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPClientThread extends Thread {
    public Handler handler;

    private String ip;
    private int port;

    DatagramSocket udpSocket;
    InetAddress serverAddr;

    UDPClientThread(String ip, int port) {
        this.ip = ip;
        this.port = port;

        try {
            udpSocket = new DatagramSocket(this.port);
            serverAddr = InetAddress.getByName(this.ip);
        } catch (SocketException e) {
            Log.e("UDP Client:", "Socket Error:", e);
        } catch (IOException e) {
            Log.e("UDP Client:", "IO Error:", e);
        }
    }

    @Override
    public void run() {
        Looper.prepare();

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                try {
                    byte[] buf = ("The String to Send").getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, port);
                    udpSocket.send(packet);
                }  catch (IOException e) {
                    Log.e("UDP Client:", "IO Error:", e);
                }
            }
        };

        Looper.loop();
    }

    public void close() {
        udpSocket.close();
    }

}
*/


import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPClientThread extends HandlerThread {
    public Handler handler;
    public Boolean running;
    private String ip;
    private int port;

    DatagramSocket udpSocket;
    InetAddress serverAddr;

    public UDPClientThread(String name) {
        super(name);
    }

    public void prepare(String ip, int port) {
        this.ip = ip;
        this.port = port;

        try {
            udpSocket = new DatagramSocket(this.port);
            serverAddr = InetAddress.getByName(this.ip);
        } catch (SocketException e) {
            Log.e("UDP Client:", "Socket Error:", e);
        } catch (IOException e) {
            Log.e("UDP Client:", "IO Error:", e);
        }

        handler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message message) {
                Bundle bundle = message.getData();
                if(bundle != null) {
                    try {
                        byte[] buf = bundle.getString("out", "error").getBytes();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, 3333);
                        udpSocket.send(packet);
                    }  catch (IOException e) {
                        Log.e("UDP Client:", "IO Error:", e);
                    }
                }
            }
        };

        running = true;
    }

}