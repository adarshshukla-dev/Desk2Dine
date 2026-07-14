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
        // Pre-fill Menu Items if database table is blank
        if (itemRepository.count() == 0) {
            createItem("Tea", 10.0, true);
            createItem("Samosa", 20.0, true);
            createItem("Coffee", 30.0, true);
            createItem("Burger", 50.0, true);
        }

        // Pre-fill a test Faculty suite
        if (facultyRepository.count() == 0) {
            Faculty fac1 = new Faculty();
            fac1.setName("Amol Sharma");
            fac1.setEmail("amol@mit.edu");
            fac1.setPassword("mit123");
            fac1.setMobileNo("9876543210");
            fac1.setCabinNo("F-204");
            fac1.setWalletBalance(500.0); // Pre-credited initial cash balance
            facultyRepository.save(fac1);
        }
    }

    private void createItem(String name, double price, boolean avail) {
        CanteenItem item = new CanteenItem();
        item.setName(name);
        item.setPrice(price);
        item.setAvailable(avail);
        item.setId(null); // Managed natively by auto-increment
        itemRepository.save(item);
    }
}