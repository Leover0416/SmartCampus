package com.simon.campus.service.ingest;

import java.util.List;

public final class VisibilityPolicy {

    public static final int ALL = 0;
    public static final int TEACHER = 1;
    public static final int LEGACY_ADMIN_AS_TEACHER = 2;
    public static final int STUDENT = 3;

    private VisibilityPolicy() {}

    public static List<Integer> visibleLevelsForViewer(int viewerLevel) {
        if (viewerLevel == TEACHER || viewerLevel == LEGACY_ADMIN_AS_TEACHER) {
            return List.of(ALL, TEACHER, LEGACY_ADMIN_AS_TEACHER);
        }
        return List.of(ALL, STUDENT);
    }

    public static boolean canView(Integer docLevel, int viewerLevel) {
        return visibleLevelsForViewer(viewerLevel).contains(docLevel == null ? ALL : docLevel);
    }

    public static boolean isValidDocumentLevel(int level) {
        return level == ALL || level == TEACHER || level == STUDENT;
    }

    public static String label(Integer level) {
        if (level == null) return "全部可见";
        return switch (level) {
            case ALL -> "全部可见";
            case TEACHER, LEGACY_ADMIN_AS_TEACHER -> "仅教师可见";
            case STUDENT -> "仅学生可见";
            default -> "未知";
        };
    }
}
