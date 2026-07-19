package in.ac.mitmeerut.desk2dine.controller;

import in.ac.mitmeerut.desk2dine.entity.Order;
import in.ac.mitmeerut.desk2dine.entity.OrderItem;
import in.ac.mitmeerut.desk2dine.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class Desk2DineController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initDatabaseSchemaAndData() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS faculties (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "email TEXT UNIQUE, " +
                    "password TEXT, " +
                    "wallet_balance REAL DEFAULT 0.0)");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS canteen_menu_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE, " +
                    "price REAL, " +
                    "available INTEGER)");

            Integer facultyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM faculties WHERE email = ?", Integer.class, "amol.sharma@mitmeerut.ac.in");
            if (facultyCount == 0) {
                jdbcTemplate.update("INSERT INTO faculties (name, email, password, wallet_balance) VALUES (?, ?, ?, ?)", 
                        "Amol Sharma", "amol.sharma@mitmeerut.ac.in", "password123", 0.0);
            }

            Integer menuCount = jdbcTemplate.queryForObject("SELECT count(*) FROM canteen_menu_items", Integer.class);
            if (menuCount == 0) {
                jdbcTemplate.update("INSERT INTO canteen_menu_items (name, price, available) VALUES (?, ?, ?)", "Tea", 10.0, 1);
                jdbcTemplate.update("INSERT INTO canteen_menu_items (name, price, available) VALUES (?, ?, ?)", "Samosa", 20.0, 1);
                jdbcTemplate.update("INSERT INTO canteen_menu_items (name, price, available) VALUES (?, ?, ?)", "Coffee", 30.0, 1);
                jdbcTemplate.update("INSERT INTO canteen_menu_items (name, price, available) VALUES (?, ?, ?)", "Burger", 50.0, 1);
            }
            System.out.println(">>> [Desk2Dine Engine] Database Bootstrap Successful! <<<");
        } catch (Exception e) {
            System.err.println(">>> [Desk2Dine Engine] Database Initialization Skipped: " + e.getMessage());
        }
    }

    @GetMapping("/")
    public String indexPage(HttpSession session) {
        if (session.getAttribute("scopedFaculty") != null) return "redirect:/place-order";
        if (session.getAttribute("canteenLoggedIn") != null) return "redirect:/canteen/dashboard";
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() { 
        return "login"; 
    }

    @GetMapping("/signup")
    public String signupPage() { 
        return "signup"; 
    }

    @PostMapping("/auth/signup")
    public String handleSignup(@RequestParam("name") String name, 
                               @RequestParam("email") String email, 
                               @RequestParam("password") String password, 
                               Model model) {
        if (!email.toLowerCase().endsWith("@mitmeerut.ac.in")) {
            model.addAttribute("signupError", "Registration Denied! Only college emails (@mitmeerut.ac.in) allowed.");
            return "signup";
        }
        try {
            Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM faculties WHERE LOWER(email) = LOWER(?)", 
                Integer.class, 
                email
            );
            
            if (existingCount != null && existingCount > 0) {
                model.addAttribute("signupError", "Registration Denied! An account with this email address already exists.");
                return "signup";
            }

            String sql = "INSERT INTO faculties (name, email, password, wallet_balance) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, name, email, password, 0.0);
            
            model.addAttribute("signupSuccess", "Account created successfully! Please sign in below.");
            return "login";
        } catch (Exception e) {
            model.addAttribute("signupError", "Registration failed due to: " + e.getMessage());
            return "signup";
        }
    }

    @PostMapping("/auth/login")
    public String handleLogin(@RequestParam("email") String email, 
                              @RequestParam("password") String password, 
                              @RequestParam("role") String role, 
                              HttpSession session, Model model) {
        
        if ("CANTEEN".equalsIgnoreCase(role)) {
            if ("admin@desk2dine.com".equalsIgnoreCase(email) && "admin123".equals(password)) {
                session.setAttribute("canteenLoggedIn", true);
                return "redirect:/canteen/dashboard";
            } else {
                model.addAttribute("loginError", "Invalid Canteen Portal Credentials!");
                return "login";
            }
        }

        if ("FACULTY".equalsIgnoreCase(role)) {
            if (!email.toLowerCase().endsWith("@mitmeerut.ac.in")) {
                model.addAttribute("loginError", "Access Denied! Only college emails (@mitmeerut.ac.in) allowed.");
                return "login";
            }

            try {
                String sql = "SELECT name FROM faculties WHERE email = ? AND password = ?";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, email, password);
                
                if (!rows.isEmpty()) {
                    String facultyName = (String) rows.get(0).get("name");
                    session.setAttribute("scopedFaculty", facultyName); 
                    session.setAttribute("facultyEmail", email);
                    return "redirect:/place-order";
                } else {
                    model.addAttribute("loginError", "Faculty Account not found or incorrect password!");
                    return "login";
                }
            } catch (Exception e) {
                model.addAttribute("loginError", "Database Error: " + e.getMessage());
                return "login";
            }
        }

        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) { 
        session.invalidate(); 
        return "redirect:/"; 
    }

    @GetMapping("/place-order")
    public String placeOrderPage(Model model, HttpSession session) {
        String activeFaculty = (String) session.getAttribute("scopedFaculty");
        if (activeFaculty == null) return "redirect:/login";

        String sql = "SELECT id, name, price, available FROM canteen_menu_items";
        List<Map<String, Object>> menuItemsFromDB = jdbcTemplate.queryForList(sql);

        model.addAttribute("facultyName", activeFaculty); 
        model.addAttribute("menuItems", menuItemsFromDB); 
        return "place-order";
    }

    @PostMapping("/order/submit")
    public String submitOrder(@RequestParam("location") String location,
                              @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
                              @RequestParam(value = "quantities", required = false) List<Double> quantities,
                              HttpSession session) {
        
        String activeFaculty = (String) session.getAttribute("scopedFaculty");
        if (activeFaculty == null) return "redirect:/login";

        if (itemIds == null || itemIds.isEmpty()) {
            return "redirect:/place-order?error=empty";
        }

        List<OrderItem> itemsList = new ArrayList<>();
        double total = 0;

        for (int i = 0; i < itemIds.size(); i++) {
            double qty = quantities.get(i);
            if (qty > 0) {
                long currentId = itemIds.get(i);
                
                String sql = "SELECT name, price, available FROM canteen_menu_items WHERE id = ?";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, currentId);
                
                if (!rows.isEmpty()) {
                    Map<String, Object> itemData = rows.get(0);
                    Object availObj = itemData.get("available");
                    boolean isAvailable = false;
                    if (availObj instanceof Number) {
                        isAvailable = ((Number) availObj).intValue() == 1;
                    } else if (availObj instanceof Boolean) {
                        isAvailable = (Boolean) availObj;
                    }

                    if (isAvailable) {
                        String name = (String) itemData.get("name");
                        double price = ((Number) itemData.get("price")).doubleValue();

                        OrderItem item = new OrderItem();
                        item.setName(name);
                        item.setPrice(price);
                        item.setQuantity(qty);
                        item.setAmount(price * qty);
                        itemsList.add(item);
                        total += item.getAmount();
                    }
                }
            }
        }

        if (itemsList.isEmpty()) {
            return "redirect:/place-order?error=empty";
        }

        Order order = new Order();
        order.setFacultyName(activeFaculty); 
        order.setDeliveryLocation(location);
        order.setStatus("PLACED"); 
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        order.setOrderTime(LocalDateTime.now().format(dtf));
        order.setItems(itemsList);
        order.setTotalAmount(total);
        
        orderRepository.save(order);

        return "redirect:/my-orders";
    }

    @GetMapping("/my-orders")
    public String myOrdersPage(Model model, HttpSession session) {
        String activeFaculty = (String) session.getAttribute("scopedFaculty");
        if (activeFaculty == null) return "redirect:/login";

        List<Order> activeOrders = orderRepository.findByFacultyNameOrderByIdDesc(activeFaculty);
        model.addAttribute("orders", activeOrders);
        model.addAttribute("facultyName", activeFaculty); 
        return "my-orders";
    }

    @PostMapping("/order/receive/{id}") 
    public String receiveOrder(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("scopedFaculty") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        if ("DELIVERED".equals(order.getStatus())) { 
            order.setStatus("RECEIVED"); 
            orderRepository.save(order); 
        } 
        return "redirect:/my-orders"; 
    }

    @PostMapping("/order/pay/{id}") 
    public String confirmPayment(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("scopedFaculty") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        if ("BILLED".equals(order.getStatus())) { 
            order.setStatus("PAID"); 
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"); 
            order.setPaymentTime(LocalDateTime.now().format(dtf)); 
            orderRepository.save(order); 
        } 
        return "redirect:/order-details/" + id; 
    }

    @GetMapping("/order-details/{id}") 
    public String orderDetails(@PathVariable("id") Long id, Model model, HttpSession session) { 
        String activeFaculty = (String) session.getAttribute("scopedFaculty");
        if (activeFaculty == null) return "redirect:/login";
        
        Order order = orderRepository.findById(id).orElseThrow(); 
        model.addAttribute("order", order); 
        model.addAttribute("facultyName", activeFaculty);
        
        if ("PAID".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) { 
            return "order-details-paid"; 
        } 
        return "order-details-received"; 
    }

    @GetMapping("/canteen/dashboard")
    public String canteenDashboard(Model model, HttpSession session) {
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        
        List<Order> allOrders = orderRepository.findAll();
        
        long totalCount = allOrders.size();
        long pendingCount = allOrders.stream().filter(o -> "PLACED".equals(o.getStatus())).count();
        long deliveredCount = allOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).count();
        long receivedCount = allOrders.stream().filter(o -> "RECEIVED".equals(o.getStatus())).count();
        long billedCount = allOrders.stream().filter(o -> "BILLED".equals(o.getStatus())).count();
        long paidCount = allOrders.stream().filter(o -> "PAID".equals(o.getStatus())).count();
        long completedCount = allOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();

        double totalSales = allOrders.stream().mapToDouble(Order::getTotalAmount).sum();
        double pendingSales = allOrders.stream().filter(o -> "PLACED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double deliveredSales = allOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double receivedSales = allOrders.stream().filter(o -> "RECEIVED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double billedSales = allOrders.stream().filter(o -> "BILLED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double paidSales = allOrders.stream().filter(o -> "PAID".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double completedSales = allOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("pendingSales", pendingSales);
        model.addAttribute("deliveredCount", deliveredCount);
        model.addAttribute("deliveredSales", deliveredSales);
        model.addAttribute("receivedCount", receivedCount);
        model.addAttribute("receivedSales", receivedSales);
        model.addAttribute("billedCount", billedCount);
        model.addAttribute("billedSales", billedSales);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("paidSales", paidSales);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("completedSales", completedSales);

        return "canteen-dashboard";
    }

    @GetMapping("/canteen/all-orders") 
    public String canteenAllOrders(Model model, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        model.addAttribute("orders", orderRepository.findAll()); 
        return "canteen-all-orders"; 
    }

    @GetMapping("/canteen/pending-orders") 
    public String canteenPendingOrders(Model model, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        List<Order> pendingOrders = orderRepository.findAll().stream()
                .filter(o -> "PLACED".equals(o.getStatus()) || "DELIVERED".equals(o.getStatus())).toList(); 
        model.addAttribute("orders", pendingOrders); 
        return "canteen-pending-orders"; 
    }

    @GetMapping("/canteen/order-details/{id}") 
    public String canteenOrderDetails(@PathVariable("id") Long id, Model model, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        model.addAttribute("order", order); 
        return "canteen-order-details"; 
    }

    @PostMapping("/canteen/order/deliver/{id}") 
    public String deliverOrder(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        if ("PLACED".equals(order.getStatus())) { 
            order.setStatus("DELIVERED"); 
            orderRepository.save(order); 
        } 
        return "redirect:/canteen/dashboard"; 
    }

    @PostMapping("/canteen/order/bill/{id}") 
    public String generateBill(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        if ("RECEIVED".equals(order.getStatus())) { 
            order.setStatus("BILLED"); 
            order.setBillNumber(String.valueOf((int)(Math.random() * 900) + 100)); 
            orderRepository.save(order); 
        } 
        return "redirect:/canteen/dashboard"; 
    }

    @PostMapping("/canteen/order/complete/{id}") 
    public String completeOrder(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        Order order = orderRepository.findById(id).orElseThrow(); 
        if ("PAID".equals(order.getStatus())) { 
            order.setStatus("COMPLETED"); 
            orderRepository.save(order); 
        } 
        return "redirect:/canteen/dashboard"; 
    }

    @GetMapping("/canteen/manage-items") 
    public String manageItems(@RequestParam(value = "search", required = false) String search, Model model, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String sql = "SELECT id, name, price, available FROM canteen_menu_items";
        List<Map<String, Object>> items = jdbcTemplate.queryForList(sql);
        
        if (search != null && !search.trim().isEmpty()) { 
            items = items.stream().filter(i -> ((String)i.get("name")).toLowerCase().contains(search.toLowerCase())).toList(); 
            model.addAttribute("searchQuery", search); 
        } else { 
            model.addAttribute("searchQuery", ""); 
        } 
        model.addAttribute("items", items); 
        return "manage-items"; 
    }

    @PostMapping("/canteen/item/delete/{id}") 
    public String deleteItem(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String sql = "DELETE FROM canteen_menu_items WHERE id = ?";
        jdbcTemplate.update(sql, id);
        return "redirect:/canteen/manage-items"; 
    }

    @GetMapping("/canteen/add-item") 
    public String addItemPage(Model model, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        model.addAttribute("item", new ItemFormWrapper()); 
        return "add-item"; 
    }

    @PostMapping("/canteen/item/save") 
    public String saveItem(@ModelAttribute("item") ItemFormWrapper newItem, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String sql = "INSERT INTO canteen_menu_items (name, price, available) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, newItem.getName(), newItem.getPrice(), newItem.isAvailable() ? 1 : 0);
        return "redirect:/canteen/manage-items"; 
    }

    @GetMapping("/canteen/item/toggle-status/{id}") 
    public String toggleItemStatus(@PathVariable("id") Long id, HttpSession session) { 
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String selectSql = "SELECT available FROM canteen_menu_items WHERE id = ?";
        Map<String, Object> row = jdbcTemplate.queryForMap(selectSql, id);
        int currentAvailable = ((Number) row.get("available")).intValue();
        int newAvailable = (currentAvailable == 1) ? 0 : 1;

        jdbcTemplate.update("UPDATE canteen_menu_items SET available = ? WHERE id = ?", newAvailable, id);
        return "redirect:/canteen/manage-items"; 
    }

    @GetMapping("/canteen/edit-item/{id}")
    public String showEditItemPage(@PathVariable("id") Long id, Model model, HttpSession session) {
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String sql = "SELECT id, name, price, available FROM canteen_menu_items WHERE id = ?";
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, id);
            ItemFormWrapper item = new ItemFormWrapper();
            item.setId(((Number) row.get("id")).longValue());
            item.setName((String) row.get("name"));
            item.setPrice(((Number) row.get("price")).doubleValue());
            
            Object availObj = row.get("available");
            boolean isAvail = false;
            if (availObj instanceof Number) {
                isAvail = ((Number) availObj).intValue() == 1;
            } else if (availObj instanceof Boolean) {
                isAvail = (Boolean) availObj;
            }
            item.setAvailable(isAvail);
            
            model.addAttribute("item", item);
            return "edit-item";
        } catch (Exception e) {
            return "redirect:/canteen/manage-items?error=notfound";
        }
    }

    @PostMapping("/canteen/item/update")
    public String updateItemData(@ModelAttribute("item") ItemFormWrapper updatedItem, HttpSession session) {
        if (session.getAttribute("canteenLoggedIn") == null) return "redirect:/login";
        String sql = "UPDATE canteen_menu_items SET name = ?, price = ?, available = ? WHERE id = ?";
        jdbcTemplate.update(sql, 
            updatedItem.getName(), 
            updatedItem.getPrice(), 
            updatedItem.isAvailable() ? 1 : 0, 
            updatedItem.getId()
        );
        return "redirect:/canteen/manage-items";
    }

    public static class ItemFormWrapper {
        private long id; 
        private String name = ""; 
        private double price = 0.0; 
        private boolean available = true;

        public ItemFormWrapper() {}
        public ItemFormWrapper(long id, String name, double price, boolean available) { 
            this.id = id; 
            this.name = name; 
            this.price = price; 
            this.available = available; 
        }
        
        public long getId() { return id; } 
        public void setId(long id) { this.id = id; }
        public String getName() { return name; } 
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; } 
        public void setPrice(double price) { this.price = price; }
        public boolean isAvailable() { return available; } 
        public void setAvailable(boolean available) { this.available = available; }
    }
}