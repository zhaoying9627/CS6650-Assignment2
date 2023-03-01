public class Payload {

    private String swiper;

    private String swipee;

    private String direction;

    public Payload (String swiper, String swipee, String direction) {
        this.swiper = swiper;
        this.swipee = swipee;
        this.direction = direction;
    }

    public String getSwiper() {
        return swiper;
    }

    public void setSwiper(String swiper) {
        this.swiper = swiper;
    }

    public String getSwipee() {
        return swipee;
    }

    public void setSwipee(String swipee) {
        this.swipee = swipee;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
