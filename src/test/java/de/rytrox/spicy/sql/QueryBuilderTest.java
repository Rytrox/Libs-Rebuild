package de.rytrox.spicy.sql;

import de.rytrox.spicy.sql.entity.Developer;
import de.rytrox.spicy.sql.entity.DeveloperWithoutMatchingConstructor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class QueryBuilderTest {

    private static SQLite datasource;

    private final CountDownLatch lock = new CountDownLatch(1);

    @BeforeAll
    public static void setupDatasource() {
        datasource = new SQLite(Paths.get("src", "test", "resources", "database.db").toFile());
    }

    @Test
    public void shouldNotConvertIntoNonEntity() {
        assertThrows(RuntimeException.class, () -> datasource.prepare("SELECT * FROM Developers")
                .query(DeveloperWithoutMatchingConstructor.class)
                .subscribe((res) -> {
                    // Hier ist gewaltig was schiefgelaufen, wenn das eintritt...
                    fail();
                }));
    }

    @Test
    public void shouldFailOnSyntaxError() {
        assertThrows(IllegalStateException.class, () -> datasource.prepare("SELECT FROM Developers")
                .query(Developer.class)
                .subscribe((res) -> {
                    // Hier ist gewaltig was schiefgelaufen, wenn das eintritt...
                    fail();
                }));
    }

    @Test
    public void shouldAllowWildcardNotationInStatement() {
        datasource.prepare("SELECT * FROM Developers WHERE id = ? AND name = ?", 408, "Timeout")
                .query(Developer.class)
                .subscribe((res) -> {
                    assertEquals(408, res.get(0).getId());
                    assertEquals("Timeout", res.get(0).getName());
                });
    }

    @Test
    public void shouldExecuteAsyncSQLSelect() throws InterruptedException {
        AtomicBoolean resultPassed = new AtomicBoolean(false);

        datasource.prepare("SELECT * FROM Developers")
                .queryAsync(Developer.class)
                .subscribe((developers) -> {
                    Developer timeout = developers.get(0);
                    Developer sether = developers.get(1);

                    assertEquals(701, sether.getId());
                    assertEquals("Sether", sether.getName());

                    assertEquals(408, timeout.getId());
                    assertEquals("Timeout", timeout.getName());

                    resultPassed.set(true);
                    lock.countDown();
                });

        assertTrue(lock.await(3, TimeUnit.SECONDS));
        assertTrue(resultPassed.get());
    }

    @Test
    public void shouldExecuteSQLSelect() {
        datasource.prepare("SELECT * FROM Developers")
                .query(Developer.class)
                .subscribe((developers) -> {
                    Developer timeout = developers.get(0);
                    Developer sether = developers.get(1);

                    assertEquals(701, sether.getId());
                    assertEquals("Sether", sether.getName());

                    assertEquals(408, timeout.getId());
                    assertEquals("Timeout", timeout.getName());
                });
    }

    @Test
    public void shouldExecuteAsyncSQLInsert() throws InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);

        datasource.prepare("INSERT INTO Developers(id, name) VALUES (404, 'Not Found')")
                .insertAsync()
                .thenRun(() -> {

                    datasource.prepare("SELECT * FROM Developers WHERE id = 404")
                            .query(Developer.class)
                            .subscribe((res) -> {
                                assertEquals(404, res.get(0).getId());
                                assertEquals("Not Found", res.get(0).getName());

                                result.set(true);

                                datasource.prepare("DELETE FROM Developers WHERE id = 404")
                                        .execute();
                                lock.countDown();
                            });
                });

        assertTrue(lock.await(3, TimeUnit.SECONDS));
        assertTrue(result.get());
    }

    @Test
    public void shouldExecuteSQLInsert() {
        datasource.prepare("INSERT INTO Developers(id, name) VALUES (404, 'Not Found')")
                .insert();

        datasource.prepare("SELECT * FROM Developers WHERE id = 404")
                .query(Developer.class)
                .subscribe((res) -> {
                    assertEquals(404, res.get(0).getId());
                    assertEquals("Not Found", res.get(0).getName());


                    datasource.prepare("DELETE FROM Developers WHERE id = 404")
                            .execute();
                });
    }

    @Test
    public void shouldUpdateAsyncSQLUpdate() throws InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);

        datasource.prepare("UPDATE Developers SET name = 'Sether701' WHERE id = 701")
                .updateAsync()
                .thenAccept((res) -> {
                    datasource.prepare("UPDATE Developers SET name = 'Sether' WHERE id = 701")
                            .update();

                    assertEquals(1, res.intValue());
                    result.set(true);

                    lock.countDown();
                });

        assertTrue(lock.await(3, TimeUnit.SECONDS));
        assertTrue(result.get());
    }

    @Test
    public void shouldExecuteSQLUpdate() {
        assertEquals(1, datasource.prepare("UPDATE Developers SET name = 'Sether701' WHERE id = 701")
                .update());

        datasource.prepare("UPDATE Developers SET name = 'Sether' WHERE id = 701")
                .update();
    }
}
