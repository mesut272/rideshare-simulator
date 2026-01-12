package finalProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Name {
    private static List<String> names = new ArrayList<>();
    static {
        names.add("Jack");
        names.add("Eva");
        names.add("Liam");
        names.add("Olivia");
        names.add("Noah");
        names.add("Emma");
        names.add("Lucas");
        names.add("Sophia");
        names.add("Mason");
        names.add("Isabella");
        names.add("Ethan");
        names.add("Mia");
        names.add("James");
        names.add("Charlotte");
        names.add("Benjamin");
        names.add("Amelia");
        names.add("Henry");
        names.add("Harper");
        names.add("Alexander");
        names.add("Ella");
        names.add("Daniel");
        names.add("Avery");
        names.add("Michael");
        names.add("Scarlett");
        names.add("William");
        names.add("Grace");
        names.add("Leo");
        names.add("Chloe");
        names.add("Sebastian");
        names.add("Lily");
    }

    public static String randomName() {
        Random rand = new Random();
        return names.get(rand.nextInt(names.size()));
    }
}
