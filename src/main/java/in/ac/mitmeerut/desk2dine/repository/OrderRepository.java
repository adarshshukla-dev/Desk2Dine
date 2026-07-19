package in.ac.mitmeerut.desk2dine.repository;

import in.ac.mitmeerut.desk2dine.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> 
{
    List<Order> findByFacultyNameOrderByIdDesc(String facultyName);
}