package finalProject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public class RideRequestGenerator {

        private static final Random rand = new Random();

        public static RideRequest generateRequest() {

                List<String> locations = CityMap.getAllLocations();

                // random starting place
                String start = locations.get(rand.nextInt(locations.size()));

                // random destination place (not same as start)
                String dest = start;
                while (dest.equals(start)) {
                        dest = locations.get(rand.nextInt(locations.size()));
                }

                // distance
                double distance = CityMap.getDistance(start, dest);

                // customer
                String name = Name.randomName();

                // 🔧 修复：只设置请求时间
                LocalDateTime requestTime = LocalDateTime.now();

                // ride type
                RideType rideType = RideType.randomRideType();

                // 🔧 修复：不再预设startTime和completionTime
                return new RideRequest(name, start, dest, distance, requestTime, rideType);
        }
}