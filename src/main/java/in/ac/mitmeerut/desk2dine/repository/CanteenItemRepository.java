package in.ac.mitmeerut.desk2dine.repository;

import in.ac.mitmeerut.desk2dine.entity.CanteenItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CanteenItemRepository extends JpaRepository<CanteenItem, Long> {
    List<CanteenItem> findByNameContainingIgnoreCase(String name);
}