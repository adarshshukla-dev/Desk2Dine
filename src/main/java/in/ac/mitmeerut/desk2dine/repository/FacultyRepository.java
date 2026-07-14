package in.ac.mitmeerut.desk2dine.repository;

import in.ac.mitmeerut.desk2dine.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByEmailAndPassword(String email, String password);
}