package click.dailyfeed.batch.domain.activity.member.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//@Slf4j
//@Service
//@RequiredArgsConstructor
public class MemberActivityDynamicFileWatcherService {
//    private final JobLauncher jobLauncher;
//    private final Job failureRecoveryJob;
//
//    @Value("${batch.failure-base-path:/kafka-failures}")
//    private String failureBasePath;
//
//    @Value("${batch.service-name:member-service}")
//    private String serviceName;
//
//    @Value("${batch.event-type:comment_create}")
//    private String eventType;
//
//    @Value("${batch.watch-days-retention:3}")
//    private int watchDaysRetention; // 며칠 전까지 감시할지
//
//    private WatchService watchService;
//    private ExecutorService executor;
//
//    // 감시 중인 디렉토리 관리: Path -> WatchKey
//    private final Map<Path, WatchKey> watchedDirectories = new ConcurrentHashMap<>();
//
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
//    private volatile boolean running = true;
//
//    @PostConstruct
//    public void init() {
//        try {
//            // WatchService 초기화
//            watchService = FileSystems.getDefault().newWatchService();
//
//            // 초기 디렉토리 등록
//            registerCurrentAndRecentDirectories();
//
//            // 파일 감시 스레드 시작
//            executor = Executors.newSingleThreadExecutor();
//            executor.submit(this::watchFiles);
//
//            log.info("Dynamic file watcher initialized. Base path: {}/{}/{}",
//                    failureBasePath, serviceName, eventType);
//        } catch (IOException e) {
//            log.error("Failed to initialize dynamic file watcher", e);
//        }
//    }
//
//    /**
//     * 1분마다 새로운 날짜 디렉토리를 확인하고 등록
//     */
//    @Scheduled(cron = "0 * * * * *") // 매분 0초에 실행
//    public void checkAndRegisterNewDirectories() {
//        try {
//            log.debug("Checking for new directories to watch...");
//
//            LocalDate today = LocalDate.now();
//
//            // 오늘부터 과거 N일까지의 디렉토리 확인
//            for (int i = 0; i <= watchDaysRetention; i++) {
//                LocalDate targetDate = today.minusDays(i);
//                String dateStr = targetDate.format(DATE_FORMATTER);
//
//                Path targetPath = Paths.get(failureBasePath, serviceName, eventType, dateStr);
//
//                // 이미 감시 중인지 확인
//                if (!watchedDirectories.containsKey(targetPath)) {
//                    // 디렉토리가 존재하거나 생성 가능한 경우 등록
//                    if (Files.exists(targetPath) || createDirectoryIfNeeded(targetPath)) {
//                        registerDirectory(targetPath);
//                    }
//                }
//            }
//
//            // 오래된 디렉토리 정리
//            cleanupOldDirectories(today);
//
//        } catch (Exception e) {
//            log.error("Error while checking new directories", e);
//        }
//    }
//
//    /**
//     * 자정마다 실행 - 날짜가 바뀔 때 새로운 날짜 디렉토리 등록
//     */
//    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
//    public void onDateChange() {
//        log.info("Date changed. Registering new date directory...");
//
//        LocalDate today = LocalDate.now();
//        String dateStr = today.format(DATE_FORMATTER);
//        Path todayPath = Paths.get(failureBasePath, serviceName, eventType, dateStr);
//
//        if (createDirectoryIfNeeded(todayPath)) {
//            registerDirectory(todayPath);
//        }
//    }
//
//    /**
//     * 초기 시작 시 현재 및 최근 디렉토리 등록
//     */
//    private void registerCurrentAndRecentDirectories() {
//        LocalDate today = LocalDate.now();
//
//        for (int i = 0; i <= watchDaysRetention; i++) {
//            LocalDate targetDate = today.minusDays(i);
//            String dateStr = targetDate.format(DATE_FORMATTER);
//            Path targetPath = Paths.get(failureBasePath, serviceName, eventType, dateStr);
//
//            if (createDirectoryIfNeeded(targetPath)) {
//                registerDirectory(targetPath);
//            }
//        }
//    }
//
//    /**
//     * 디렉토리를 WatchService에 등록
//     */
//    private void registerDirectory(Path directory) {
//        try {
//            if (!Files.exists(directory)) {
//                log.warn("Directory does not exist: {}", directory);
//                return;
//            }
//
//            if (watchedDirectories.containsKey(directory)) {
//                log.debug("Directory already being watched: {}", directory);
//                return;
//            }
//
//            WatchKey key = directory.register(
//                    watchService,
//                    StandardWatchEventKinds.ENTRY_CREATE,
//                    StandardWatchEventKinds.ENTRY_MODIFY
//            );
//
//            watchedDirectories.put(directory, key);
//            log.info("Registered directory for watching: {} (Total: {})",
//                    directory, watchedDirectories.size());
//
//        } catch (IOException e) {
//            log.error("Failed to register directory: {}", directory, e);
//        }
//    }
//
//    /**
//     * 디렉토리가 없으면 생성
//     */
//    private boolean createDirectoryIfNeeded(Path directory) {
//        try {
//            if (!Files.exists(directory)) {
//                Files.createDirectories(directory);
//                log.info("Created directory: {}", directory);
//            }
//            return true;
//        } catch (IOException e) {
//            log.error("Failed to create directory: {}", directory, e);
//            return false;
//        }
//    }
//
//    /**
//     * 오래된 디렉토리 감시 해제 및 정리
//     */
//    private void cleanupOldDirectories(LocalDate currentDate) {
//        LocalDate cutoffDate = currentDate.minusDays(watchDaysRetention);
//
//        watchedDirectories.entrySet().removeIf(entry -> {
//            Path path = entry.getKey();
//            String dirName = path.getFileName().toString();
//
//            try {
//                // 디렉토리명이 날짜 형식인지 확인
//                LocalDate dirDate = LocalDate.parse(dirName, DATE_FORMATTER);
//
//                // 보관 기간이 지난 경우
//                if (dirDate.isBefore(cutoffDate)) {
//                    WatchKey key = entry.getValue();
//                    key.cancel();
//                    log.info("Removed old directory from watch: {} (Date: {})",
//                            path, dirName);
//                    return true;
//                }
//            } catch (Exception e) {
//                log.debug("Not a date directory, keeping: {}", dirName);
//            }
//
//            return false;
//        });
//    }
//
//    /**
//     * 파일 감시 루프
//     */
//    private void watchFiles() {
//        log.info("File watching thread started");
//
//        try {
//            while (running) {
//                WatchKey key = watchService.poll(
//                        java.util.concurrent.TimeUnit.SECONDS.toMillis(1),
//                        java.util.concurrent.TimeUnit.MILLISECONDS
//                );
//
//                if (key == null) {
//                    continue;
//                }
//
//                // 어느 디렉토리에서 발생한 이벤트인지 찾기
//                Path watchedDir = findWatchedDirectory(key);
//
//                for (WatchEvent<?> event : key.pollEvents()) {
//                    WatchEvent.Kind<?> kind = event.kind();
//
//                    if (kind == StandardWatchEventKinds.OVERFLOW) {
//                        log.warn("WatchEvent overflow occurred");
//                        continue;
//                    }
//
//                    Path filename = (Path) event.context();
//                    Path fullPath = watchedDir != null ?
//                            watchedDir.resolve(filename) : filename;
//
//                    // JSON 파일만 처리
//                    if (filename.toString().endsWith(".json")) {
//                        log.info("File event detected: {} in {} (Type: {})",
//                                filename, watchedDir, kind.name());
//
//                        // 배치 작업 트리거
//                        triggerBatchJob(watchedDir, filename);
//                    }
//                }
//
//                boolean valid = key.reset();
//                if (!valid) {
//                    log.warn("WatchKey no longer valid, removing from watched directories");
//                    watchedDirectories.values().remove(key);
//                }
//            }
//        } catch (InterruptedException e) {
//            log.error("File watcher interrupted", e);
//            Thread.currentThread().interrupt();
//        } catch (Exception e) {
//            log.error("Error in file watching loop", e);
//        }
//
//        log.info("File watching thread stopped");
//    }
//
//    /**
//     * WatchKey에 해당하는 디렉토리 찾기
//     */
//    private Path findWatchedDirectory(WatchKey key) {
//        return watchedDirectories.entrySet().stream()
//                .filter(entry -> entry.getValue().equals(key))
//                .map(Map.Entry::getKey)
//                .findFirst()
//                .orElse(null);
//    }
//
//    /**
//     * 배치 작업 트리거
//     */
//    private void triggerBatchJob(Path directory, Path filename) {
//        try {
//            JobParameters jobParameters = new JobParametersBuilder()
//                    .addLong("time", System.currentTimeMillis())
//                    .addString("directory", directory != null ? directory.toString() : "")
//                    .addString("filename", filename.toString())
//                    .toJobParameters();
//
//            jobLauncher.run(failureRecoveryJob, jobParameters);
//            log.info("Batch job triggered for file: {}/{}", directory, filename);
//
//        } catch (Exception e) {
//            log.error("Failed to trigger batch job for file: {}", filename, e);
//        }
//    }
//
//    /**
//     * 현재 감시 중인 디렉토리 목록 조회
//     */
//    public Map<Path, WatchKey> getWatchedDirectories() {
//        return new ConcurrentHashMap<>(watchedDirectories);
//    }
//
//    /**
//     * 수동으로 특정 날짜 디렉토리 등록
//     */
//    public void registerDateDirectory(LocalDate date) {
//        String dateStr = date.format(DATE_FORMATTER);
//        Path targetPath = Paths.get(failureBasePath, serviceName, eventType, dateStr);
//
//        if (createDirectoryIfNeeded(targetPath)) {
//            registerDirectory(targetPath);
//        }
//    }
//
//    @PreDestroy
//    public void cleanup() {
//        running = false;
//
//        try {
//            // 모든 WatchKey 취소
//            watchedDirectories.values().forEach(WatchKey::cancel);
//            watchedDirectories.clear();
//
//            if (watchService != null) {
//                watchService.close();
//                log.info("WatchService closed");
//            }
//
//            if (executor != null) {
//                executor.shutdown();
//                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
//                    executor.shutdownNow();
//                }
//                log.info("Executor service shutdown");
//            }
//        } catch (Exception e) {
//            log.error("Error during cleanup", e);
//        }
//    }
}
