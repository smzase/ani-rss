package ani.rss.notification;

import ani.rss.commons.NumberFormatUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.NotificationConfig;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.StringEnum;
import ani.rss.service.DownloadService;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.ItemsUtil;
import ani.rss.util.other.RenameUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface BaseNotification {
    Pattern NOTIFICATION_EPISODE_RANGE_PATTERN = Pattern.compile("[Ss](\\d+)[Ee](\\d+(?:\\.5)?)(?:\\s*[-~_\\uFF5E\\uFF0D\\u2010-\\u2015\\u2212]\\s*(\\d+(?:\\.5)?))?");

    /**
     * 测试
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     */
    void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    /**
     * 发送通知
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 是否成功
     */
    Boolean send(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    default String replaceNotificationTemplate(Ani ani, NotificationConfig notificationConfig, String text, NotificationStatusEnum notificationStatusEnum) {
        String notificationTemplate = notificationConfig.getNotificationTemplate();

        String comment = Opt.ofNullable(notificationConfig)
                .map(NotificationConfig::getComment)
                .filter(StrUtil::isNotBlank)
                .orElse("无备注");

        notificationTemplate = notificationTemplate.replace("${comment}", comment);

        return replaceNotificationTemplate(ani, notificationTemplate, text, notificationStatusEnum);
    }

    default String replaceNotificationTemplate(Ani ani, String notificationTemplate, String text, NotificationStatusEnum notificationStatusEnum) {
        notificationTemplate = notificationTemplate.replace("${text}", text);

        // 集数
        double episode = 1.0;
        String episodeText = NumberFormatUtils.format(episode, 1, 0);
        String episodeFormat = String.format("%02d", (int) episode);
        Matcher matcher = NOTIFICATION_EPISODE_RANGE_PATTERN.matcher(text);
        if (matcher.find()) {
            String startEpisode = matcher.group(2);
            String endEpisode = matcher.group(3);
            episode = Double.parseDouble(startEpisode);
            episodeText = getEpisodeText(startEpisode);
            episodeFormat = getEpisodeFormat(startEpisode);
            if (StrUtil.isNotBlank(endEpisode)) {
                episodeText = episodeText + "~" + getEpisodeText(endEpisode);
                episodeFormat = episodeFormat + "~" + getEpisodeFormat(endEpisode);
            }
        }

        notificationTemplate = notificationTemplate.replace("${episode}", episodeText);
        notificationTemplate = notificationTemplate.replace("${episodeFormat}", episodeFormat);


        Date releaseDate = ani.getReleaseDate();
        int year = DateUtil.year(releaseDate);
        int month = DateUtil.month(releaseDate) + 1;
        int date = DateUtil.dayOfMonth(releaseDate);

        notificationTemplate = notificationTemplate.replace("${year}", String.valueOf(year));
        notificationTemplate = notificationTemplate.replace("${month}", String.valueOf(month));
        notificationTemplate = notificationTemplate.replace("${date}", String.valueOf(date));

        List<Func1<Ani, Object>> list = List.of(
                Ani::getTitle,
                Ani::getScore,
                Ani::getSeason,
                Ani::getThemoviedbName,
                Ani::getBgmUrl,
                Ani::getCurrentEpisodeNumber,
                Ani::getTotalEpisodeNumber,
                Ani::getSubgroup
        );

        int season = ani.getSeason();
        String seasonFormat = String.format("%02d", season);
        notificationTemplate = notificationTemplate.replace("${seasonFormat}", seasonFormat);

        notificationTemplate = RenameUtil.replaceField(notificationTemplate, ani, list);

        String tmdbId = Optional.of(ani)
                .map(Ani::getTmdb)
                .map(Tmdb::getId)
                .filter(StrUtil::isNotBlank)
                .orElse("");
        notificationTemplate = notificationTemplate.replace("${tmdbid}", tmdbId);

        String tmdbUrl = "";
        if (StrUtil.isNotBlank(tmdbId)) {
            Boolean ova = Opt.ofNullable(ani)
                    .map(Ani::getOva)
                    .orElse(false);
            String type = ova ? "movie" : "tv";
            tmdbUrl = StrFormatter.format("https://www.themoviedb.org/{}/{}", type, tmdbId);
        }
        notificationTemplate = notificationTemplate.replace("${tmdburl}", tmdbUrl);

        String emoji = notificationStatusEnum.getEmoji();
        String action = notificationStatusEnum.getAction();

        notificationTemplate = notificationTemplate.replace("${emoji}", emoji);
        notificationTemplate = notificationTemplate.replace("${action}", action);

        String downloadPath = SpringUtil.getBean(DownloadService.class).getDownloadPath(ani);
        notificationTemplate = notificationTemplate.replace("${downloadPath}", downloadPath);

        if (notificationTemplate.contains("${jpTitle}")) {
            String jpTitle = RenameUtil.getJpTitle(ani);
            notificationTemplate = notificationTemplate.replace("${jpTitle}", jpTitle);
        }

        notificationTemplate = RenameUtil.replaceEpisodeTitle(notificationTemplate, episode, ani);

        if (!notificationTemplate.contains("${notification}")) {
            return notificationTemplate.trim();
        }

        Config config = ConfigUtil.CONFIG;
        String template = config.getNotificationTemplate();

        template = replaceNotificationTemplate(ani, template, text, notificationStatusEnum);

        notificationTemplate = notificationTemplate.replace("${notification}", template);

        return notificationTemplate.trim();
    }

    private static String getEpisodeText(String episodeStr) {
        double episode = Double.parseDouble(episodeStr);
        return NumberFormatUtils.format(episode, 1, 0);
    }

    private static String getEpisodeFormat(String episodeStr) {
        double episode = Double.parseDouble(episodeStr);
        String episodeFormat = String.format("%02d", (int) episode);
        if (ItemsUtil.is5(episode)) {
            episodeFormat = episodeFormat + ".5";
        }
        return episodeFormat;
    }
}
