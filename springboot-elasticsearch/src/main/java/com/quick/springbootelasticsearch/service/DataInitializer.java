package com.quick.springbootelasticsearch.service;

import com.quick.springbootelasticsearch.model.Product;
import com.quick.springbootelasticsearch.model.Product.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 启动时自动创建测试数据。
 * <p>
 * 生产项目请删除此类，或用 {@code @Profile("dev")} 限定环境。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductService productService;

    @Override
    public void run(String... args) {
        // 检查是否已有数据，避免重复插入
        if (productService.findAll().size() >= 5) {
            log.info("[数据初始化] 已有 {} 条数据，跳过初始化",
                    productService.findAll().size());
            return;
        }

        log.info("[数据初始化] 开始插入测试商品...");

        List<Product> products = List.of(
                Product.builder()
                        .name("华为Mate60 Pro 智能手机")
                        .description("搭载麒麟9000S芯片，支持5G网络，卫星通话，XMAGE影像系统")
                        .brand("华为")
                        .category("电子产品")
                        .tags(List.of("5G", "旗舰", "拍照", "卫星通话"))
                        .price(new BigDecimal("6999"))
                        .stock(500)
                        .rating(4.9)
                        .onSale(true)
                        .imageUrl("https://img.example.com/huawei-mate60.jpg")
                        .location(new GeoPoint(22.5431, 114.0579))
                        .suggest(new Completion("华为", 10))
                        .skus(List.of(
                                new Sku("SKU001", "雅丹黑", "256GB", new BigDecimal("6999"), 200),
                                new Sku("SKU002", "雅丹黑", "512GB", new BigDecimal("7999"), 150),
                                new Sku("SKU003", "白沙银", "256GB", new BigDecimal("6999"), 150)
                        ))
                        .build(),

                Product.builder()
                        .name("iPhone 15 Pro Max")
                        .description("A17 Pro芯片，钛金属设计，4800万像素主摄，USB-C接口")
                        .brand("Apple")
                        .category("电子产品")
                        .tags(List.of("5G", "旗舰", "iOS", "钛金属"))
                        .price(new BigDecimal("9999"))
                        .stock(300)
                        .rating(4.8)
                        .onSale(true)
                        .imageUrl("https://img.example.com/iphone15.jpg")
                        .location(new GeoPoint(22.3080, 113.9180))
                        .suggest(new Completion("iPhone", 9))
                        .skus(List.of(
                                new Sku("SKU004", "原色钛金属", "256GB", new BigDecimal("9999"), 100),
                                new Sku("SKU005", "蓝色钛金属", "512GB", new BigDecimal("11999"), 200)
                        ))
                        .build(),

                Product.builder()
                        .name("小米14 Ultra")
                        .description("骁龙8Gen3处理器，徕卡光学镜头，1英寸大底，90W快充")
                        .brand("小米")
                        .category("电子产品")
                        .tags(List.of("5G", "徕卡", "快充", "旗舰"))
                        .price(new BigDecimal("5999"))
                        .stock(800)
                        .rating(4.7)
                        .onSale(true)
                        .imageUrl("https://img.example.com/xiaomi14u.jpg")
                        .location(new GeoPoint(39.9042, 116.4074))
                        .suggest(new Completion("小米", 8))
                        .skus(List.of(
                                new Sku("SKU006", "黑色", "256GB", new BigDecimal("5999"), 500),
                                new Sku("SKU007", "白色", "256GB", new BigDecimal("5999"), 300)
                        ))
                        .build(),

                Product.builder()
                        .name("OPPO Find X7 Ultra")
                        .description("双潜望长焦，哈苏人像，5000mAh大电池，100W超级闪充")
                        .brand("OPPO")
                        .category("电子产品")
                        .tags(List.of("5G", "拍照", "快充", "哈苏"))
                        .price(new BigDecimal("5999"))
                        .stock(400)
                        .rating(4.6)
                        .onSale(true)
                        .imageUrl("https://img.example.com/oppo-x7u.jpg")
                        .location(new GeoPoint(23.1291, 113.2644))
                        .suggest(new Completion("OPPO", 7))
                        .skus(List.of(
                                new Sku("SKU008", "大漠银月", "512GB", new BigDecimal("5999"), 400)
                        ))
                        .build(),

                Product.builder()
                        .name("华为MateBook X Pro 2024")
                        .description("3.1K OLED原色屏，酷睿Ultra处理器，980g超轻机身")
                        .brand("华为")
                        .category("电脑办公")
                        .tags(List.of("轻薄本", "OLED", "商务"))
                        .price(new BigDecimal("8999"))
                        .stock(200)
                        .rating(4.8)
                        .onSale(true)
                        .imageUrl("https://img.example.com/matebook-xpro.jpg")
                        .location(new GeoPoint(22.5431, 114.0579))
                        .suggest(new Completion("华为", 10))
                        .skus(List.of(
                                new Sku("SKU009", "砚黑", "16GB+1TB", new BigDecimal("8999"), 100),
                                new Sku("SKU010", "皓月银", "16GB+512GB", new BigDecimal("7999"), 100)
                        ))
                        .build(),

                Product.builder()
                        .name("MacBook Pro 14英寸 M3 Pro")
                        .description("M3 Pro芯片，Liquid Retina XDR屏，18小时续航")
                        .brand("Apple")
                        .category("电脑办公")
                        .tags(List.of("M3", "macOS", "专业"))
                        .price(new BigDecimal("14999"))
                        .stock(100)
                        .rating(4.9)
                        .onSale(true)
                        .imageUrl("https://img.example.com/macbook-pro.jpg")
                        .location(new GeoPoint(22.3080, 113.9180))
                        .suggest(new Completion("MacBook", 9))
                        .skus(List.of(
                                new Sku("SKU011", "深空黑", "18GB+512GB", new BigDecimal("14999"), 50),
                                new Sku("SKU012", "银色", "36GB+1TB", new BigDecimal("19999"), 50)
                        ))
                        .build(),

                Product.builder()
                        .name("Sony WH-1000XM5 头戴式降噪耳机")
                        .description("行业标杆降噪，30小时续航，LDAC高清传输")
                        .brand("Sony")
                        .category("影音设备")
                        .tags(List.of("降噪", "无线", "Hi-Res"))
                        .price(new BigDecimal("2299"))
                        .stock(600)
                        .rating(4.7)
                        .onSale(true)
                        .imageUrl("https://img.example.com/sony-xm5.jpg")
                        .location(new GeoPoint(35.6762, 139.6503))
                        .suggest(new Completion("Sony", 6))
                        .skus(List.of(
                                new Sku("SKU013", "黑色", "标准版", new BigDecimal("2299"), 300),
                                new Sku("SKU014", "铂金银", "标准版", new BigDecimal("2299"), 300)
                        ))
                        .build(),

                Product.builder()
                        .name("AirPods Pro 第二代")
                        .description("H2芯片，自适应降噪，个性化空间音频，USB-C")
                        .brand("Apple")
                        .category("影音设备")
                        .tags(List.of("降噪", "无线", "Apple生态"))
                        .price(new BigDecimal("1899"))
                        .stock(400)
                        .rating(4.8)
                        .onSale(true)
                        .imageUrl("https://img.example.com/airpods-pro.jpg")
                        .location(new GeoPoint(22.3080, 113.9180))
                        .suggest(new Completion("AirPods", 8))
                        .skus(List.of(
                                new Sku("SKU015", "白色", "标准版", new BigDecimal("1899"), 400)
                        ))
                        .build(),

                Product.builder()
                        .name("戴尔U2723QE 27英寸4K显示器")
                        .description("IPS Black技术，4K分辨率，Type-C 90W充电，专业色准")
                        .brand("Dell")
                        .category("电脑办公")
                        .tags(List.of("4K", "专业", "Type-C"))
                        .price(new BigDecimal("3999"))
                        .stock(150)
                        .rating(4.6)
                        .onSale(true)
                        .imageUrl("https://img.example.com/dell-u2723.jpg")
                        .location(new GeoPoint(31.2304, 121.4737))
                        .suggest(new Completion("Dell", 5))
                        .skus(List.of(
                                new Sku("SKU016", "黑色", "27英寸", new BigDecimal("3999"), 150)
                        ))
                        .build(),

                Product.builder()
                        .name("华为Watch GT 4 智能手表")
                        .description("1.43英寸AMOLED屏，14天超长续航，科学运动指导")
                        .brand("华为")
                        .category("智能穿戴")
                        .tags(List.of("运动", "健康", "长续航"))
                        .price(new BigDecimal("1488"))
                        .stock(1000)
                        .rating(4.5)
                        .onSale(true)
                        .imageUrl("https://img.example.com/huawei-gt4.jpg")
                        .location(new GeoPoint(22.5431, 114.0579))
                        .suggest(new Completion("华为", 10))
                        .skus(List.of(
                                new Sku("SKU017", "幻夜黑", "46mm", new BigDecimal("1488"), 500),
                                new Sku("SKU018", "山茶棕", "41mm", new BigDecimal("1288"), 500)
                        ))
                        .build()
        );

        productService.saveBatch(products);
        log.info("[数据初始化] 已插入 {} 条测试商品", products.size());
    }
}
