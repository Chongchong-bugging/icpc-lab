package cn.edu.scuec.icpc.lab.importing;

import cn.edu.scuec.icpc.lab.identity.PersonRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 会员基础档案导入（FR-06 数据准备，DOM-24）：姓名/学号必填，学号全局唯一。
 * 只做人员主数据，不创建账号、不授予角色（权限仍由账号流程处理）。
 */
@Component
public class MemberImportHandler implements ImportHandler {

    static final List<String> COLUMNS = List.of("姓名", "学号", "邮箱", "电话", "专业", "年级");

    private final PersonRepository persons;
    private final JdbcTemplate jdbc;

    public MemberImportHandler(PersonRepository persons, JdbcTemplate jdbc) {
        this.persons = persons;
        this.jdbc = jdbc;
    }

    @Override
    public String kind() {
        return "MEMBER";
    }

    @Override
    public List<String> requiredColumns() {
        return COLUMNS;
    }

    @Override
    public List<RowIssue> validate(List<Map<String, String>> rows) {
        List<RowIssue> issues = new ArrayList<>();
        Set<String> seenStudentNos = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            String name = value(row, "姓名");
            String studentNo = value(row, "学号");
            String email = value(row, "邮箱");
            String phone = value(row, "电话");
            String major = value(row, "专业");
            String grade = value(row, "年级");

            if (name == null) {
                issues.add(new RowIssue(index, "REQUIRED_FIELD", "姓名", "姓名不能为空", null));
            } else if (name.length() > 64) {
                issues.add(new RowIssue(index, "FIELD_TOO_LONG", "姓名", "姓名不能超过 64 字", name));
            }
            if (studentNo == null) {
                issues.add(new RowIssue(index, "REQUIRED_FIELD", "学号", "学号不能为空", null));
            } else if (!studentNo.matches("[A-Za-z0-9_-]{4,32}")) {
                issues.add(new RowIssue(index, "INVALID_STUDENT_NO", "学号", "学号格式非法", studentNo));
            } else if (!seenStudentNos.add(studentNo.toUpperCase(Locale.ROOT))) {
                issues.add(new RowIssue(index, "DUPLICATE_STUDENT_NO_IN_FILE", "学号", "文件内学号重复", studentNo));
            } else if (persons.findByStudentNo(studentNo).isPresent()) {
                issues.add(new RowIssue(index, "STUDENT_NO_EXISTS", "学号", "学号已存在", studentNo));
            }
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                issues.add(new RowIssue(index, "INVALID_EMAIL", "邮箱", "邮箱格式非法", email));
            }
            if (phone != null && !phone.matches("[0-9+\\- ]{6,32}")) {
                issues.add(new RowIssue(index, "INVALID_PHONE", "电话", "电话格式非法", phone));
            }
            if (major != null && major.length() > 128) {
                issues.add(new RowIssue(index, "FIELD_TOO_LONG", "专业", "专业不能超过 128 字", major));
            }
            if (grade != null) {
                try {
                    int parsed = Integer.parseInt(grade);
                    if (parsed < 1 || parsed > 10) {
                        throw new NumberFormatException("out of range");
                    }
                } catch (NumberFormatException ex) {
                    issues.add(new RowIssue(index, "INVALID_GRADE", "年级", "年级需为 1-10 的整数", grade));
                }
            }
        }
        return issues;
    }

    @Override
    public void apply(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            String name = value(row, "姓名");
            String studentNo = value(row, "学号");
            String grade = value(row, "年级");
            jdbc.update("insert into person (id, student_no, name, email, phone, major, grade)"
                            + " values (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), studentNo, name, value(row, "邮箱"), value(row, "电话"),
                    value(row, "专业"), grade == null ? null : Integer.parseInt(grade));
        }
    }

    private String value(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }
}