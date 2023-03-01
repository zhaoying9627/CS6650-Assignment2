import com.google.gson.Gson;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.pool2.*;
import org.apache.commons.pool2.impl.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "TwinderServlet", value = "/TwinderServlet")
public class TwinderServlet extends HttpServlet {
    private final static int SWIPER_BOUND = 5000;

    private final static int SWIPEE_BOUND = 1000000;

    private final static int COMMENT_LENGTH = 256;

    private final static String LEFT = "left";

    private final static String RIGHT = "right";

    // Number of channels to add to pools
    private static final int NUM_CHANS = 50;
    // RMQ broker machine
    private static final String SERVER = "35.92.112.39";
//    private static final String SERVER = "localhost";
    // set queue name
    private static final String FIRST_QUEUE_NAME = "test1";
    private static final String SECOND_QUEUE_NAME = "test2";
    // the duration in seconds a client waits for a channel to be available in the pool
    // Tune value to meet request load and pass to config.setMaxWait(...) method
    private static final int WAIT_TIME_SECS = 1;

    private GenericObjectPool<Channel> pool;

    @Override
    public void init() {
        // we use this object to tailor the behavior of the GenericObjectPool
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        // limit the pool size
        config.setMaxTotal(NUM_CHANS);
        // clients will block when pool is exhausted, for a maximum duration of WAIT_TIME_SECS
        config.setBlockWhenExhausted(true);
        // tune WAIT_TIME_SECS to meet your workload/demand
        config.setMaxWaitMillis(Duration.ofSeconds(WAIT_TIME_SECS).toMillis());
        // config factory
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SERVER);
        factory.setUsername("admin");
        factory.setPassword("password");
        try {
            // create a new connection
            final Connection conn = factory.newConnection();
            // The channel factory generates new channels on demand, as needed by the GenericObjectPool
            RMQChannelFactory chanFactory = new RMQChannelFactory (conn);
            // create the pool
            pool = new GenericObjectPool<>(chanFactory, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String urlPath = request.getPathInfo();
        System.out.println(urlPath);

        // check we have a URL
        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("missing paramterers");
            return;
        }

        String[] urlParts = urlPath.split("/");

        // validate url and return the response
        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid inputs");
            return;
        }
        Gson gson = new Gson();
        try {
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = request.getReader().readLine()) != null) {
                sb.append(s);
            }
            SwipeDetails swipeDetails = gson.fromJson(sb.toString(), SwipeDetails.class);
            // validate swiper
            if (!(1 <= Integer.parseInt(swipeDetails.getSwiper()) &&
                    Integer.parseInt(swipeDetails.getSwiper()) <= SWIPER_BOUND)){
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write(" User not found");
                return;
            }

            // validate swipee
            if(!(1 <= Integer.parseInt(swipeDetails.getSwipee()) &&
                    Integer.parseInt(swipeDetails.getSwipee()) <= SWIPEE_BOUND)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write(" User not found");
                return;
            }

            // validate comment
            if(!(1 <= swipeDetails.getComment().length() &&
                    swipeDetails.getComment().length() <= COMMENT_LENGTH)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid inputs");
                return;
            }

            // write successful
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write("Write successful");

            // create and convert payload to json string
            Payload payload = new Payload(swipeDetails.getSwiper(),
                    swipeDetails.getSwipee(), urlParts[1]);

            String payloadString = gson.toJson(payload);

            try {
                Channel channel;
                // get a channel from the pool
                channel = pool.borrowObject();

                // publish a message
                channel.queueDeclare(FIRST_QUEUE_NAME, false, false, false, null);
                channel.queueDeclare(SECOND_QUEUE_NAME, false, false, false, null);
                byte[] message = payloadString.getBytes();
                channel.basicPublish("", FIRST_QUEUE_NAME, null, message);
                channel.basicPublish("", SECOND_QUEUE_NAME, null, message);
                // return the channel to the pool
                pool.returnObject(channel);

            } catch (IOException ex) {
                Logger.getLogger(TwinderServlet.class.getName()).log(Level.INFO, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(TwinderServlet.class.getName()).log(Level.INFO, null, ex);
            }
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid inputs");
        }
    }

    @Override
    public void destroy() {
        // close the pool
        super.destroy();
        // close the channels and shutdown the pool
        pool.close();
    }

    private boolean isUrlValid (String[] urlParts) {
        // check whether length equals two
        if(urlParts.length < 2) {
            return false;
        }
        // check whether equals left or right
        if(!urlParts[1].equals(LEFT) && !urlParts[1].equals(RIGHT)) {
            return false;
        }
        return true;
    }
}
