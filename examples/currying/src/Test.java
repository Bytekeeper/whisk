import org.apache.logging.log4j.LogManager;

public class Test {
    private static org.apache.logging.log4j.Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        logger.info("works");
    }
}