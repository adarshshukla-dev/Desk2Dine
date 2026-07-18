package in.ac.mitmeerut.desk2dine.controller;

import in.ac.mitmeerut.desk2dine.entity.Order;
import in.ac.mitmeerut.desk2dine.entity.OrderItem;
import in.ac.mitmeerut.desk2dine.repository.OrderRepository;
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

    // ================= 🏠 PUBLIC HOME & BRAND LANDING PAGE =================

    @GetMapping("/")
    public String indexPage(HttpSession session) {
        // Active dynamic sessions ko check karke automatic standard portal router par target bypass loop lagaya hai
        if (session.getAttribute("scopedFaculty") != null) {
            return "redirect:/place-order";
        } else if (session.getAttribute("canteenLoggedIn") != null) {
            return "redirect:/canteen/dashboard";
        }
        return "index"; // Render index.html marketing/introductory deck webpage
    }

    // ================= 🔑 REAL DATABASE AUTHENTICATION =================

    @GetMapping("/login")
    public String loginPage() {
        return "login"; 
    }

    @PostMapping("/auth/login")
    public String handleLogin(@RequestParam("email") String email, 
                              @RequestParam("password") String password, 
                              @RequestParam("role") String role, 
                              HttpSession session, Model model) {
        
        // 1. CANTEEN PORTAL SECURITY MATRIX
        if ("CANTEEN".equalsIgnoreCase(role)) {
            if ("admin@desk2dine.com".equalsIgnoreCase(email) && "admin123".equals(password)) {
                session.setAttribute("canteenLoggedIn", true);
                return "redirect:/canteen/dashboard";
            } else {
                model.addAttribute("loginError", "Invalid Canteen Portal Credentials!");
                return "login";
            }
        }

        // 2. FACULTY PORTAL SECURITY MATRIX WITH @mitmeerut.ac.in DOMAIN CONSTRAINT
        if ("FACULTY".equalsIgnoreCase(role)) {
            if (!email.toLowerCase().endsWith("@mitmeerut.ac.in")) {
                model.addAttribute("loginError", "Access Denied! Only college emails (@mitmeerut.ac.in) allowed.");
                return "login";
            }

            try {
                // Fetch details from the 'faculties' table inside your desk2dine.db
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
        return "redirect:/"; // Session validation expire hote hi standard return home page layout par reverse karega
    }

    // ================= FACULTY PORTAL FLOW =================

    @GetMapping("/place-order")
    public String placeOrderPage(Model model, HttpSession session) {
        String activeFaculty = (String) session.getAttribute("scopedFaculty");
        if (activeFaculty == null) return "redirect:/login"; // Force real login intercept

        // 🍕 REAL DATABASE FETCH: Fetching active menu directly from 'canteen_menu_items' table
        String sql = "SELECT id, name, price, available FROM canteen_menu_items";
        List<Map<String, Object>> menuItemsFromDB = jdbcTemplate.queryForList(sql);

        model.addAttribute("facultyName", activeFaculty); 
        model.addAttribute("menuItems", menuItemsFromDB); // Inject real dynamic dataset to Thymeleaf
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

        // Database logic tracking for dynamic items submission
        List<OrderItem> itemsList = new ArrayList<>();
        double total = 0;

        for (int i = 0; i < itemIds.size(); i++) {
            double qty = quantities.get(i);
            if (qty > 0) {
                long currentId = itemIds.get(i);
                
                // Real DB Validation check for order submission integrity
                String sql = "SELECT name, price, available FROM canteen_menu_items WHERE id = ?";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, currentId);
                
                if (!rows.isEmpty()) {
                    Map<String, Object> itemData = rows.get(0);
                    // Checking dynamic integer/boolean state from SQLite database mapping
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

    // ================= 🏪 CANTEEN PORTAL FLOW =================

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

    // ================= CANTEEN PORTAL INVENTORY MANAGEMENT =================

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

    // ================= ITEM LOGISTICS STRUCT MODEL FORM DATA BOUND =================
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