# 一畦日记

一个无需构建工具的静态天气与照片日记，记录阜阳奶油白菜从播种开始的每一天。

## 现在有什么

- **实时天气卡片**：页面打开时通过 Open-Meteo 读取阜阳当前天气。
- **每日天气档案**：GitHub Actions 每天 09:25（北京时间）自动抓取前一天的天气并提交到 `garden/data/weather.json`。
- **生长手记**：`garden/data/photos.json` 控制照片、日期、天数和观察文字，照片放在 `garden/photos/`。
- **每日提醒**：页面内可开启浏览器通知，提醒拍照；通知设置保存在当前浏览器里。
- **响应式设计**：手机、平板和桌面端均可阅读，GitHub Pages 可直接部署。

## 如何把照片放进网站

这是一个静态网站，浏览器里的“上传”无法直接写回 GitHub 仓库。每天把照片发到当前聊天，我会按下面的方式帮你更新：

1. 将照片放到 `garden/photos/YYYY-MM-DD.jpg`；
2. 在 `garden/data/photos.json` 的 `days` 数组新增一条记录；
3. 提交并推送到 GitHub Pages。

推荐记录格式：

```json
{
  "date": "2026-11-20",
  "day": 1,
  "label": "第一片叶子",
  "note": "今天看起来比昨天更挺了一点。",
  "photo": "photos/2026-11-20.jpg",
  "source": "photo"
}
```

## 每日提醒的边界

浏览器通知是增强功能，不是云端定时任务：需要用户授权，并且页面或浏览器后台机制允许通知。关闭浏览器、系统省电或手机回收后台时，可能不会准时提醒。若要稳定提醒，建议同时在手机系统日历/闹钟设置一个每日提醒。

## GitHub Pages

仓库根目录已经通过 `index.html` 跳转到 `garden/`。在仓库设置中打开 **Pages → Deploy from a branch → main / root** 即可发布。
