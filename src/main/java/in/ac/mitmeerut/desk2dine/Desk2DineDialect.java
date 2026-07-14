package in.ac.mitmeerut.desk2dine;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.DatabaseVersion;

public class Desk2DineDialect extends Dialect {
    
    public Desk2DineDialect() {
        super();
    }

    @Override
    public DatabaseVersion getVersion() {
        // Hibernate 7 ke signature ke mutabik version object return kar rahe hain
        return DatabaseVersion.make(3);
    }
}