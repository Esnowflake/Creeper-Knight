# 友尽权杖贴图草稿

当前选用第二版：

- `friendship_scepter-v2-16.png`：16×16 RGBA PNG，游戏用贴图，背景透明。
- `friendship_scepter-v2-preview.png`：上述实际贴图的 512×512 最近邻放大预览。
- `friendship_scepter-v2-source.png`：内置 imagegen 生成的源图。
- `prompt-v2.txt`：第二版完整生成提示。

设计对应合成材料：绿色苦力怕头部、青蓝色钻石连接件、金色握柄。源图导出时裁掉上下多余透明留白，按最近邻采样为 16×16，保留原图 alpha；预览由实际 16×16 贴图逐像素放大。

第一版文件保留为过程草稿。第二版 16×16 贴图已用于 0.2.0 的友尽权杖物品，游戏资源位于 `common/src/main/resources/assets/creeperknight/textures/item/friendship_scepter.png`。
