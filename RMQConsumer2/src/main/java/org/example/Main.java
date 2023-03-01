package org.example;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
public class Main {
    private final static String QUEUE_NAME = "test2";
    private final static String SERVER = "35.92.112.39";
//    private final static String SERVER = "localhost";
    private final static int THREAD_NUMBER = 200;
    private final static String RIGHT = "right";
    private static Map<String, List<String>> likedUsers = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, TimeoutException {
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
                        // update liked users
                        if (payload.getDirection().equals(RIGHT)) {
                            if (!likedUsers.containsKey(userid)) {
                                likedUsers.put(userid, new ArrayList<>());
                            }
                            String swipee = payload.getSwipee();
                            int listSize = likedUsers.get(userid).size();
                            if (listSize >= 100) {
                                likedUsers.get(userid).set(listSize - 1, swipee);
                            } else {
                                likedUsers.get(userid).add(swipee);
                            }
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