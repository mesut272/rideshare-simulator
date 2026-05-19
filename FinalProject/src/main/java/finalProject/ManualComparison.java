package finalProject;

/**
 * Manual Strategy Comparison Helper
 *
 * Instructions:
 * 1. Run RideSharingApp with config.properties set to COMPOSITE
 * 2. Record the metrics below
 * 3. Change config to NEAREST_DRIVER and run again
 * 4. Change config to LOAD_BALANCING and run again
 * 5. Compare results
 *
 * This class just provides a template for recording results.
 */
public class ManualComparison {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("MANUAL STRATEGY COMPARISON");
        System.out.println("=".repeat(70));
        System.out.println();

        System.out.println("Instructions:");
        System.out.println("1. Edit config.properties, set: simulation.dispatch.strategy=COMPOSITE");
        System.out.println("2. Run RideSharingApp and record metrics");
        System.out.println("3. Change to NEAREST_DRIVER and run again");
        System.out.println("4. Change to LOAD_BALANCING and run again");
        System.out.println("5. Fill in the table below");
        System.out.println();

        System.out.println("Results Template:");
        System.out.println("-".repeat(70));
        System.out.println("Strategy           | Avg Wait | Max Wait | Min Wait | Avg Ride");
        System.out.println("-".repeat(70));
        System.out.println("COMPOSITE          | ___.__s  | ___s     | ___s     | ___.__s");
        System.out.println("NEAREST_DRIVER     | ___.__s  | ___s     | ___s     | ___.__s");
        System.out.println("LOAD_BALANCING     | ___.__s  | ___s     | ___s     | ___.__s");
        System.out.println("-".repeat(70));
        System.out.println();

        printKnownResults();
    }

    private static void printKnownResults() {
        System.out.println("Known Results (from previous tests):");
        System.out.println("-".repeat(70));
        System.out.println("Strategy           | Avg Wait | Max Wait | Min Wait | Avg Ride");
        System.out.println("-".repeat(70));
        System.out.println("COMPOSITE          | 0.30s    | 1s       | 0s       | 4.90s");
        System.out.println("NEAREST_DRIVER     | 2.70s    | 12s      | 0s       | 8.70s");
        System.out.println("-".repeat(70));
        System.out.println();

        System.out.println("Analysis:");
        System.out.println("- COMPOSITE: Fast and consistent (best average wait)");
        System.out.println("- NEAREST_DRIVER: Slower due to search overhead");
        System.out.println("- LOAD_BALANCING: [Add your findings]");
        System.out.println();
    }
}
