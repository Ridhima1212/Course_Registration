import java.io.*;
import java.nio.file.*;
import java.util.*;

// ===== Custom Exception =====
class UniversityException extends Exception {
    public UniversityException(String message) { super(message); }
}

// ===== Interface =====
interface Registrable {
    void enroll(String studentId) throws UniversityException;
    void drop(String studentId) throws UniversityException;
    int seatsLeft();
}

// ===== Abstract User (Encapsulation + Abstraction) =====
abstract class User {
    private final String id;
    private String name;
    private String email;

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public abstract String role();

    @Override
    public String toString() { return role() + " " + name + " (" + id + ") <" + email + ">"; }
}

// ===== Student / Instructor (Inheritance) =====
class Student extends User {
    private String program;
    public Student(String id, String name, String email, String program) {
        super(id, name, email);
        this.program = program;
    }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    @Override public String role() { return "Student"; }
}

class Instructor extends User {
    private String department;
    public Instructor(String id, String name, String email, String department) {
        super(id, name, email);
        this.department = department;
    }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    @Override public String role() { return "Instructor"; }
}

// ===== Abstract Course (Abstraction + Polymorphism) =====
abstract class Course implements Registrable {
    private final String code;
    private String title;
    private int capacity;
    private final Set<String> enrolled = new LinkedHashSet<>();
    private Instructor instructor;

    protected Course(String code, String title, int capacity, Instructor instructor) {
        this.code = code;
        this.title = title;
        this.capacity = capacity;
        this.instructor = instructor;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Instructor getInstructor() { return instructor; }
    public void setInstructor(Instructor instructor) { this.instructor = instructor; }
    public Set<String> getEnrolled() { return Collections.unmodifiableSet(enrolled); }

    @Override
    public void enroll(String studentId) throws UniversityException {
        if (enrolled.contains(studentId)) throw new UniversityException("Already enrolled.");
        if (enrolled.size() >= capacity) throw new UniversityException("Course full.");
        enrolled.add(studentId);
    }

    @Override
    public void drop(String studentId) throws UniversityException {
        if (!enrolled.remove(studentId)) throw new UniversityException("Student not in course.");
    }

    @Override
    public int seatsLeft() { return capacity - enrolled.size(); }

    // Polymorphic parts
    public abstract int credits();
    public abstract double feePerCredit();
    public double totalFee() { return credits() * feePerCredit(); }
    public abstract String kind();

    @Override
    public String toString() {
        return String.format("%s %s (%s) | by %s | seats: %d/%d | credits=%d, fee=\u20B9%.2f",
                kind(), code, title, instructor != null ? instructor.getName() : "TBA",
                enrolled.size(), capacity, credits(), totalFee());
    }
}

// ===== Concrete Courses (Polymorphism) =====
class TheoryCourse extends Course {
    public TheoryCourse(String code, String title, int capacity, Instructor inst) { super(code, title, capacity, inst); }
    @Override public int credits() { return 3; }
    @Override public double feePerCredit() { return 1500.0; }
    @Override public String kind() { return "Theory"; }
}

class LabCourse extends Course {
    public LabCourse(String code, String title, int capacity, Instructor inst) { super(code, title, capacity, inst); }
    @Override public int credits() { return 2; }
    @Override public double feePerCredit() { return 2000.0; } // labs costlier
    @Override public String kind() { return "Lab"; }
}

class ProjectCourse extends Course {
    public ProjectCourse(String code, String title, int capacity, Instructor inst) { super(code, title, capacity, inst); }
    @Override public int credits() { return 4; }
    @Override public double feePerCredit() { return 1200.0; } // discounted for project
    @Override public String kind() { return "Project"; }
}

// ===== Minimal CSV helper =====
class CSV {
    public static List<String[]> read(Path p) {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(p)) return rows;
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) rows.add(line.split(",", -1));
        } catch (IOException e) { System.out.println("CSV read error: " + e.getMessage()); }
        return rows;
    }
    public static void writeAll(Path p, List<String[]> rows) {
        try (BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String[] r : rows) { bw.write(String.join(",", r)); bw.newLine(); }
        } catch (IOException e) { System.out.println("CSV write error: " + e.getMessage()); }
    }
    public static void append(Path p, String[] row) {
        try (BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(String.join(",", row)); bw.newLine();
        } catch (IOException e) { System.out.println("CSV append error: " + e.getMessage()); }
    }
}

// ===== Registrar (Manager + Persistence) =====
class Registrar {
    private final Map<String, Student> students = new LinkedHashMap<>();
    private final Map<String, Instructor> instructors = new LinkedHashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();

    private static final Path STUDENTS = Paths.get("students.csv");
    private static final Path INSTRUCTORS = Paths.get("instructors.csv");
    private static final Path COURSES = Paths.get("courses.csv");
    private static final Path ENROLLMENTS = Paths.get("enrollments.csv"); // studentId,courseCode

    public Registrar() {
        loadInstructors();
        loadStudents();
        loadCourses();
        loadEnrollments();
        if (courses.isEmpty()) seedDemo();
    }

    private void seedDemo() {
        Instructor i1 = instructors.values().stream().findFirst().orElseGet(() -> {
            Instructor i = new Instructor("T01", "Dr. Meera", "meera@univ.edu", "CSE");
            instructors.put(i.getId(), i); saveInstructors(); return i;
        });
        addCourse(new TheoryCourse("CS101", "Programming Basics", 3, i1));
        addCourse(new LabCourse("CS101L", "Programming Lab", 2, i1));
        addCourse(new ProjectCourse("CS399", "Mini Project", 2, i1));
    }

    // CRUD helpers
    public void addStudent(Student s) { students.put(s.getId(), s); saveStudents(); }
    public void addInstructor(Instructor i) { instructors.put(i.getId(), i); saveInstructors(); }
    public void addCourse(Course c) { courses.put(c.getCode(), c); saveCourses(); }

    public Student getStudent(String id) { return students.get(id); }
    public Course getCourse(String code) { return courses.get(code); }

    public Collection<Student> listStudents() { return students.values(); }
    public Collection<Instructor> listInstructors() { return instructors.values(); }
    public Collection<Course> listCourses() { return courses.values(); }

    // Enrollment actions
    public void enroll(String studentId, String courseCode) throws UniversityException {
        Student s = students.get(studentId);
        if (s == null) throw new UniversityException("Student not found.");
        Course c = courses.get(courseCode);
        if (c == null) throw new UniversityException("Course not found.");
        c.enroll(studentId);
        CSV.append(ENROLLMENTS, new String[]{studentId, courseCode});
    }

    public void drop(String studentId, String courseCode) throws UniversityException {
        Course c = courses.get(courseCode);
        if (c == null) throw new UniversityException("Course not found.");
        c.drop(studentId);
        // rewrite enrollments
        List<String[]> rows = CSV.read(ENROLLMENTS);
        rows.removeIf(r -> r.length >= 2 && r[0].equals(studentId) && r[1].equals(courseCode));
        CSV.writeAll(ENROLLMENTS, rows);
    }

    // Persistence loaders/savers
    private void loadStudents() {
        for (String[] r : CSV.read(STUDENTS)) {
            if (r.length < 4) continue;
            students.put(r[0], new Student(r[0], r[1], r[2], r[3]));
        }
        if (students.isEmpty()) {
            addStudent(new Student("S01", "Riya Agarwal", "riya@univ.edu", "B.Tech CSE"));
            addStudent(new Student("S02", "Aditya Singh", "adi@univ.edu", "B.Tech CSE"));
        }
    }
    private void saveStudents() {
        List<String[]> rows = new ArrayList<>();
        for (Student s : students.values())
            rows.add(new String[]{s.getId(), s.getName(), s.getEmail(), s.getProgram()});
        CSV.writeAll(STUDENTS, rows);
    }

    private void loadInstructors() {
        for (String[] r : CSV.read(INSTRUCTORS)) {
            if (r.length < 4) continue;
            instructors.put(r[0], new Instructor(r[0], r[1], r[2], r[3]));
        }
        if (instructors.isEmpty()) {
            addInstructor(new Instructor("T01", "Dr. Meera", "meera@univ.edu", "CSE"));
            addInstructor(new Instructor("T02", "Prof. Arjun", "arjun@univ.edu", "ECE"));
        }
    }
    private void saveInstructors() {
        List<String[]> rows = new ArrayList<>();
        for (Instructor i : instructors.values())
            rows.add(new String[]{i.getId(), i.getName(), i.getEmail(), i.getDepartment()});
        CSV.writeAll(INSTRUCTORS, rows);
    }

    private void loadCourses() {
        for (String[] r : CSV.read(COURSES)) {
            if (r.length < 5) continue;
            String kind = r[0], code = r[1], title = r[2];
            int capacity = Integer.parseInt(r[3]);
            Instructor inst = instructors.get(r[4]);
            Course c = switch (kind) {
                case "Theory" -> new TheoryCourse(code, title, capacity, inst);
                case "Lab" -> new LabCourse(code, title, capacity, inst);
                case "Project" -> new ProjectCourse(code, title, capacity, inst);
                default -> null;
            };
            if (c != null) courses.put(code, c);
        }
    }
    private void saveCourses() {
        List<String[]> rows = new ArrayList<>();
        for (Course c : courses.values())
            rows.add(new String[]{c.kind(), c.getCode(), c.getTitle(), String.valueOf(c.getCapacity()),
                    c.getInstructor() != null ? c.getInstructor().getId() : ""});
        CSV.writeAll(COURSES, rows);
    }

    private void loadEnrollments() {
        for (String[] r : CSV.read(ENROLLMENTS)) {
            if (r.length < 2) continue;
            Course c = courses.get(r[1]);
            if (c != null) {
                try { c.enroll(r[0]); } catch (UniversityException ignored) {}
            }
        }
    }
}

// ===== Main App (Console UI + try-catch error handling) =====
public class CourseApp {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Registrar reg = new Registrar();

        System.out.println("Welcome to Course Registration OOP Demo (Single File)");
        int ch;
        do {
            System.out.println("\n--- Menu ---");
            System.out.println("1. List courses");
            System.out.println("2. List students");
            System.out.println("3. Enroll student to course");
            System.out.println("4. Drop student from course");
            System.out.println("5. Add new student");
            System.out.println("6. Exit");
            System.out.print("Choice: ");
            while (!sc.hasNextInt()) { System.out.print("Enter a number: "); sc.next(); }
            ch = sc.nextInt(); sc.nextLine();

            try {
                switch (ch) {
                    case 1 -> reg.listCourses().forEach(c -> System.out.println(" - " + c));
                    case 2 -> reg.listStudents().forEach(s -> System.out.println(" - " + s));
                    case 3 -> {
                        System.out.print("Student ID: ");
                        String sid = sc.nextLine().trim();
                        System.out.print("Course Code: ");
                        String code = sc.nextLine().trim();
                        reg.enroll(sid, code);
                        System.out.println("Enrolled successfully.");
                    }
                    case 4 -> {
                        System.out.print("Student ID: ");
                        String sid = sc.nextLine().trim();
                        System.out.print("Course Code: ");
                        String code = sc.nextLine().trim();
                        reg.drop(sid, code);
                        System.out.println("Dropped successfully.");
                    }
                    case 5 -> {
                        System.out.print("Student ID (e.g., S10): ");
                        String id = sc.nextLine().trim();
                        System.out.print("Name: ");
                        String name = sc.nextLine().trim();
                        System.out.print("Email: ");
                        String email = sc.nextLine().trim();
                        System.out.print("Program: ");
                        String prog = sc.nextLine().trim();
                        reg.addStudent(new Student(id, name, email, prog));
                        System.out.println("Student added.");
                    }
                    case 6 -> System.out.println("Goodbye!");
                    default -> System.out.println("Invalid.");
                }
            } catch (UniversityException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected: " + e.getMessage());
            }
        } while (ch != 6);
        sc.close();
    }
}
