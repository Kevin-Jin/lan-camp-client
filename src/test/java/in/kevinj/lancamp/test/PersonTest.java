package in.kevinj.lancamp.test;

import static org.junit.Assert.assertNotEquals;
import in.kevinj.lancamp.nativeimpl.ActiveWindowInfo;

import org.junit.Test;

public class PersonTest {
    @Test
    public void canConstructAPersonWithAName() {
        assertNotEquals(ActiveWindowInfo.INSTANCE.getActiveWindowApplication(), null);
    }
}
