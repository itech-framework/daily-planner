package org.flexitech.framework.products.daily_planner.service.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.flexitech.framework.products.daily_planner.model.AgendaItem;
import org.flexitech.framework.products.daily_planner.model.Setting;
import org.flexitech.framework.products.daily_planner.service.SettingsService;
import org.flexitech.framework.products.daily_planner.service.TaskService;
import org.flexitech.framework.products.daily_planner.session.UserSession;

import io.github.itech_framework.core.annotations.components.levels.Service;
import io.github.itech_framework.core.annotations.dependency.Use;
import io.github.itech_framework.java_fx.router.Router;
import io.github.itech_framework.java_fx.utils.notification.AppNotification;
import io.github.itech_framework.java_fx.utils.notification.NotificationSource;
import io.github.itech_framework.java_fx.utils.notification.SystemNotificationService;

@Service
public class TaskReminderSource implements NotificationSource {

    @Use
    private SettingsService settings;

    @Use
    private TaskService taskService;

    @Use
    private UserSession session;

    @Use
    private Router router;

    private volatile LocalDate lastDay;

    @Override
    public String name() {
        return "tasks";
    }

    @Override
    public List<AppNotification> check(LocalDateTime now) throws Exception {
        LocalDate today = now.toLocalDate();
        int hour = settings.getInt(Setting.TASK_HOUR);
        if (hour < 0 || session.getUser() == null || now.getHour() < hour || today.equals(lastDay)) {
            return List.of();
        }
        List<AgendaItem> open = taskService.getTasks(true).stream()
                .filter(task -> !task.done() && task.due() != null).toList();
        lastDay = today;
        long dueToday = open.stream().filter(task -> task.due().equals(today)).count();
        long overdue = open.stream().filter(task -> task.due().isBefore(today)).count();
        if (dueToday == 0 && overdue == 0) {
            return List.of();
        }
        String message = dueToday + " due today" + (overdue > 0 ? ", " + overdue + " overdue" : "");
        return List.of(new AppNotification("tasks:" + today, "Tasks for today", message,
                overdue > 0 ? SystemNotificationService.Type.WARNING : SystemNotificationService.Type.INFO,
                () -> router.off("calendar", today)));
    }
}