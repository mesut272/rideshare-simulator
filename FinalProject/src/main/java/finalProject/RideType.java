package finalProject;

import java.util.Random;

public enum RideType {
    EXPRESS_PICKUP,
    STANDARD_PICKUP,
    WAIT_AND_SAVE_PICKUP,

    ENVIR_PICKUP;
    private static final Random RAND = new Random();

    // generating random RideType with different weight
    public static RideType randomRideType() {
        int r = RAND.nextInt(100);
        if (r < 20) return EXPRESS_PICKUP;        // 20%
        if (r < 60) return STANDARD_PICKUP;       // 40%
        if (r < 85) return WAIT_AND_SAVE_PICKUP;  // 25%
        return ENVIR_PICKUP;                      // 15%
    }
}
