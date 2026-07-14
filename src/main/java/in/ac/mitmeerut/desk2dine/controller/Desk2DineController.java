package in.ac.mitmeerut.desk2dine.controller;

import in.ac.mitmeerut.desk2dine.entity.Order;
import in.ac.mitmeerut.desk2dine.entity.OrderItem;
import in.ac.mitmeerut.desk2dine.entity.CanteenItem;
import in.ac.mitmeerut.desk2dine.entity.Faculty;
import in.ac.mitmeerut.desk2dine.repository.OrderRepository;
import in.ac.mitmeerut.desk2dine.repository.CanteenItemRepository;
import in.ac.mitmeerut.desk2dine.repository.FacultyRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
public class Desk2DineController {
	
	@Autowired
	private FacultyRepository facultyRepository;

	@Autowired
	private in.ac.mitmeerut.desk2dine.repository.FeedbackRepository feedbackRepository;

	// Session Tracking State Variables
	private String loggedInUserEmail = null;

	@GetMapping("/faculty/login")
	public String facultyLoginPage() {
	    return "faculty-login";
	}

	@PostMapping("/faculty/login/process")
	public String processAuthentication(@RequestParam("email") String email, 
	                                    @RequestParam("password") String password, 
	                                    Model model) {
	    java.util.Optional<Faculty> user = facultyRepository.findByEmailAndPassword(email, password);
	    if (user.isPresent()) {
	        loggedInUserEmail = email; // Global session initialized
	        return "redirect:/place-order";
	    }
	    return "redirect:/faculty/login?error=true";
	}

	@GetMapping("/faculty/logout")
	public String processLogout() {
	    loggedInUserEmail = null; // Session cleared
	    return "redirect:/";
	}
	
	@GetMapping("/")
	public String indexGateway() {
	    return "index";
	}

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CanteenItemRepository itemRepository;

    // Default User Setup (Jab tak login system nahi hai, "Amol Sharma" default use karenge)
    private final String CURRENT_FACULTY = "Amol Sharma";

    @GetMapping("/place-order")
    public String placeOrderPage(Model model) {
        model.addAttribute("facultyName", CURRENT_FACULTY);
        return "place-order";
    }

    // Dynamic Form Submission Handler
    @PostMapping("/order/submit")
    public String submitOrder(@RequestParam("location") String location,
                              @RequestParam(value = "teaQty", defaultValue = "0") double teaQty,
                              @RequestParam(value = "samosaQty", defaultValue = "0") double samosaQty) {
        
        if (teaQty == 0 && samosaQty == 0) {
            return "redirect:/place-order?error=empty";
        }

        Order order = new Order();
        order.setFacultyName(CURRENT_FACULTY);
        order.setDeliveryLocation(location);
        order.setStatus("PLACED");
        
        // Current Time Format parser
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        order.setOrderTime(LocalDateTime.now().format(dtf));

        List<OrderItem> itemsList = new ArrayList<>();
        double total = 0;

        if (teaQty > 0) {
            OrderItem item = new OrderItem();
            item.setName("Tea");
            item.setPrice(10.0);
            item.setQuantity(teaQty);
            item.setAmount(10.0 * teaQty);
            itemsList.add(item);
            total += item.getAmount();
        }

        if (samosaQty > 0) {
            OrderItem item = new OrderItem();
            item.setName("Samosa");
            item.setPrice(20.0);
            item.setQuantity(samosaQty);
            item.setAmount(20.0 * samosaQty);
            itemsList.add(item);
            total += item.getAmount();
        }

        order.setItems(itemsList);
        order.setTotalAmount(total);

        // Database Table level creation
        orderRepository.save(order);

        return "redirect:/my-orders";
    }

    @GetMapping("/my-orders")
    public String myOrdersPage(Model model) {
        List<Order> activeOrders = orderRepository.findByFacultyNameOrderByIdDesc(CURRENT_FACULTY);
        model.addAttribute("orders", activeOrders);
        return "my-orders";
    }

    // Dynamic State Transition Route: PLACED -> RECEIVED
    @PostMapping("/order/receive/{id}")
    public String receiveOrder(@PathVariable("id") Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setStatus("RECEIVED");
        orderRepository.save(order);
        return "redirect:/my-orders";
    }

    @GetMapping("/order-details/{id}")
    public String orderDetails(@PathVariable("id") Long id, Model model) {
        Order order = orderRepository.findById(id).orElseThrow();
        model.addAttribute("order", order);
        
        if ("PAID".equals(order.getStatus())) {
            return "order-details-paid";
        }
        return "order-details-received";
    }

    // Dynamic State Transition Route: RECEIVED -> PAID
    @PostMapping("/order/pay/{id}")
    public String payOrder(@PathVariable("id") Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setStatus("PAID");
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
        order.setPaymentTime(LocalDateTime.now().format(dtf));
        order.setBillNumber(String.valueOf((int)(Math.random() * 100) + 1)); // Random reference bill no.
        
        orderRepository.save(order);
        return "redirect:/order-details/" + id;
    }

    // --- CANTEEN PORTAL ENDPOINTS ---

    @GetMapping("/canteen/dashboard")
    public String canteenDashboard(Model model) {
        List<Order> allOrders = orderRepository.findAll();
        
        // Core counter variables
        long totalCount = allOrders.size();
        long pendingCount = allOrders.stream().filter(o -> "PLACED".equals(o.getStatus())).count();
        long deliveredCount = allOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).count();
        long receivedCount = allOrders.stream().filter(o -> "RECEIVED".equals(o.getStatus())).count();
        long paidCount = allOrders.stream().filter(o -> "PAID".equals(o.getStatus())).count();
        long completedCount = allOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();

        // Sales calculator loops
        double totalSales = allOrders.stream().mapToDouble(Order::getTotalAmount).sum();
        double pendingSales = allOrders.stream().filter(o -> "PLACED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double deliveredSales = allOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double receivedSales = allOrders.stream().filter(o -> "RECEIVED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double paidSales = allOrders.stream().filter(o -> "PAID".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();
        double completedSales = allOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).mapToDouble(Order::getTotalAmount).sum();

        model.addAttribute("totalCount", totalCount).addAttribute("totalSales", totalSales);
        model.addAttribute("pendingCount", pendingCount).addAttribute("pendingSales", pendingSales);
        model.addAttribute("deliveredCount", deliveredCount).addAttribute("deliveredSales", deliveredSales);
        model.addAttribute("receivedCount", receivedCount).addAttribute("receivedSales", receivedSales);
        model.addAttribute("paidCount", paidCount).addAttribute("paidSales", paidSales);
        model.addAttribute("completedCount", completedCount).addAttribute("completedSales", completedSales);

        return "canteen-dashboard";
    }

    @GetMapping("/canteen/all-orders")
    public String canteenAllOrders(Model model) {
        model.addAttribute("orders", orderRepository.findAll());
        return "canteen-all-orders";
    }

    @GetMapping("/canteen/pending-orders")
    public String canteenPendingOrders(Model model) {
        // Only fetch orders that are PLACED
        List<Order> pendingOrders = orderRepository.findAll().stream()
                .filter(o -> "PLACED".equals(o.getStatus())).toList();
        model.addAttribute("orders", pendingOrders);
        return "canteen-pending-orders";
    }

    @GetMapping("/canteen/order-details/{id}")
    public String canteenOrderDetails(@PathVariable("id") Long id, Model model) {
        Order order = orderRepository.findById(id).orElseThrow();
        model.addAttribute("order", order);
        return "canteen-order-details";
    }

    // Action 1: PLACED -> DELIVERED
    @PostMapping("/canteen/order/deliver/{id}")
    public String deliverOrder(@PathVariable("id") Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if ("PLACED".equals(order.getStatus())) {
            order.setStatus("DELIVERED");
            orderRepository.save(order);
        }
        return "redirect:/canteen/all-orders";
    }

    // Action 2: PAID -> COMPLETED
    @PostMapping("/canteen/order/complete/{id}")
    public String completeOrder(@PathVariable("id") Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if ("PAID".equals(order.getStatus())) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
        }
        return "redirect:/canteen/all-orders";
    }

    // --- MANAGE CANTEEN ITEMS ENDPOINTS ---

    @GetMapping("/canteen/manage-items")
    public String manageItems(@RequestParam(value = "search", required = false) String search, 
                              @RequestParam(value = "error", required = false) String error, 
                              Model model) {
        List<CanteenItem> items;
        if (search != null && !search.trim().isEmpty()) {
            items = itemRepository.findByNameContainingIgnoreCase(search);
            model.addAttribute("searchQuery", search);
        } else {
            items = itemRepository.findAll();
        }
        
        if (error != null) {
            model.addAttribute("errorMessage", "Item can not be deleted. The item is part of orders placed earlier.");
        }
        
        model.addAttribute("items", items);
        return "manage-items";
    }

    @GetMapping("/canteen/add-item")
    public String addItemPage(Model model) {
        model.addAttribute("item", new CanteenItem());
        return "add-item";
    }

    @PostMapping("/canteen/item/save")
    public String saveItem(@ModelAttribute CanteenItem item) {
        itemRepository.save(item);
        return "redirect:/canteen/manage-items";
    }

    @GetMapping("/canteen/edit-item/{id}")
    public String editItemPage(@PathVariable("id") Long id, Model model) {
        CanteenItem item = itemRepository.findById(id).orElseThrow();
        model.addAttribute("item", item);
        return "edit-item";
    }

    @PostMapping("/canteen/item/update")
    public String updateItem(@ModelAttribute CanteenItem item) {
        itemRepository.save(item);
        return "redirect:/canteen/manage-items";
    }

    @PostMapping("/canteen/item/delete/{id}")
    public String deleteItem(@PathVariable("id") Long id) {
        CanteenItem item = itemRepository.findById(id).orElseThrow();
        
        // **Asli Business Validation Matrix Check**
        // Pata lagao ki kya ye item pehle kisi order ka hissa tha
        boolean isUsedInOrders = orderRepository.findAll().stream()
            .flatMap(o -> o.getItems().stream())
            .anyMatch(oi -> oi.getName().equalsIgnoreCase(item.getName()));
            
        if (isUsedInOrders) {
            return "redirect:/canteen/manage-items?error=restricted";
        }
        
        itemRepository.delete(item);
        return "redirect:/canteen/manage-items";
    }
}