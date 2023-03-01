package org.example;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private final static String QUEUE_NAME = "test1";

    private final static String SERVER = "35.92.112.39";
//    private final static String SERVER = "localhost";
    private final static int THREAD_NUMBER = 200;
    private final static String RIGHT = "right";
    private static Map<String,Integer> countLike = new ConcurrentHashMap<>();
    private static Map<String,Integer> countDislike = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SERVER);
        factory.setUsername("admin");
        factory.setPassword("password");
        final Connection connection = factory.newConnection();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final Channel channel = connection.createChannel();
                    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                    // max one message per consumer
                    channel.basicQos(1);
                    System.out.println(" [*] Thread waiting for messages. To exit press CTRL+C");

                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        String message = new String(delivery.getBody(), "UTF-8");
                        Gson gson = new Gson();
                        // convert message to payload
                        Payload payload = gson.fromJson(message, Payload.class);
                        // get userid
                        String userid = payload.getSwiper();
                        // update countLike or countDislike
                        if (payload.getDirection().equals(RIGHT)) {
                            countLike.put(userid, countLike.getOrDefault(userid, 0) + 1);
                        } else {
                            countDislike.put(userid, countDislike.getOrDefault(userid, 0) + 1);
                        }
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        System.out.println( "Callback thread ID = " + Thread.currentThread().getId() + " Received '" + message + "'");
                    };
                    // process messages
                    channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        // start threads and block to receive messages
        for (int i = 0; i < THREAD_NUMBER; i++) {
            Thread newThread = new Thread(runnable);
            newThread.start();
        }
    }
}