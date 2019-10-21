import static org.junit.Assert.*;
import org.junit.*;

import java.net.URL;

public class MinimalBuildTest {
    @org.junit.Test
    public void shouldHaveCopiedResources() {
        // WHEN
        URL resource = getClass().getResource("blub.txt");

        // THEN
        assertNotNull(resource);
    }
}