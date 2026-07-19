package in.ac.mitmeerut.desk2dine;

import in.ac.mitmeerut.desk2dine.entity.CanteenItem;
import in.ac.mitmeerut.desk2dine.entity.Faculty;
import in.ac.mitmeerut.desk2dine.repository.CanteenItemRepository;
import in.ac.mitmeerut.desk2dine.repository.FacultyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private CanteenItemRepository itemRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Override
    public void run(String... args) throws Exception {
        if (itemRepository.count() == 0) {
            createItem("Tea", 10.0, true);
            createItem("Samosa", 20.0, true);
            createItem("Coffee", 30.0, true);
            createItem("Burger", 50.0, true);
        }

        if (facultyRepository.count() == 0) {
            Faculty fac1 = new Faculty();
            fac1.setName("Amol Sharma");
            fac1.setEmail("amol.sharma@mitmeerut.ac.in");
            fac1.setPassword("password123");
            fac1.setMobileNo("9876543210");
            fac1.setCabinNo("F-204");
            fac1.setWalletBalance(500.0);
            facultyRepository.save(fac1);
        }
    }

    private void createItem(String name, double price, boolean avail) {
        CanteenItem item = new CanteenItem();
        item.setName(name);
        item.setPrice(price);
        item.setAvailable(avail);
        itemRepository.save(item);
    }
}