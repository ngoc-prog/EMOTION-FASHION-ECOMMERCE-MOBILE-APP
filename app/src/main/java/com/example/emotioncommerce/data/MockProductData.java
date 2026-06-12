package com.example.emotioncommerce.data;

import com.example.emotioncommerce.model.Product;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MockProductData {

    // picsum.photos: direct CDN, no redirect, consistent per seed, always HTTPS
    private static String img(int seed) {
        return "https://picsum.photos/seed/" + seed + "/400/500";
    }

    private static ArrayList<String> imgs(int seed) {
        ArrayList<String> list = new ArrayList<>();
        list.add("https://picsum.photos/seed/" + seed + "/400/500");
        list.add("https://picsum.photos/seed/" + (seed + 50) + "/400/500");
        list.add("https://picsum.photos/seed/" + (seed + 100) + "/400/500");
        return list;
    }

    public static List<Product> getProducts() {
        return Arrays.asList(
            // Serum
            new Product(1, "Serum Dưỡng Ẩm Hyaluronic Acid",
                "Dưỡng chất hyaluronic acid 3 phân tử giúp da căng mọng, mờ nếp nhăn và cấp ẩm sâu suốt 24h. Phù hợp mọi loại da kể cả da nhạy cảm.",
                480000L, 0, "Serum", img(11), "ÉLAN", imgs(11)),

            new Product(2, "Serum Vitamin C 20% Làm Sáng Da",
                "Nồng độ Vitamin C 20% kết hợp Ferulic Acid giúp làm sáng đều màu da, mờ thâm nám và bảo vệ da khỏi gốc tự do. Da rạng rỡ sau 2 tuần.",
                650000L, 0, "Serum", img(22), "ÉLAN", imgs(22)),

            new Product(3, "Serum Retinol 0.5% Chống Lão Hóa",
                "Retinol 0.5% encapsulated tan chảy từ từ, kích thích tái tạo tế bào da, mờ nếp nhăn và làm đều kết cấu da. Dùng buổi tối, 2-3 lần/tuần.",
                890000L, 0, "Serum", img(33), "ÉLAN", imgs(33)),

            new Product(4, "Serum Niacinamide 10% Thu Nhỏ Lỗ Chân Lông",
                "Niacinamide 10% + Zinc 1% kiểm soát dầu thừa, thu nhỏ lỗ chân lông và giảm mụn đầu đen hiệu quả. Kết cấu nước nhẹ, thấm siêu nhanh.",
                420000L, 0, "Serum", img(44), "ÉLAN", imgs(44)),

            new Product(5, "Serum Peptide Tái Tạo Collagen",
                "Phức hợp 5 peptide kích thích sản sinh collagen tự nhiên, cải thiện độ đàn hồi và làm căng da từ bên trong. Kết quả rõ ràng sau 4 tuần.",
                780000L, 0, "Serum", img(55), "ÉLAN", imgs(55)),

            new Product(6, "Serum AHA BHA Tẩy Tế Bào Chết",
                "AHA 10% + BHA 2% hòa tan tế bào chết, thông thoáng lỗ chân lông và làm đều màu da. Dùng 2-3 đêm/tuần, kết hợp kem chống nắng ban ngày.",
                550000L, 0, "Serum", img(66), "ÉLAN", imgs(66)),

            // Dưỡng ẩm
            new Product(7, "Kem Dưỡng Ẩm Rose Petal",
                "Chiết xuất cánh hoa hồng Bulgaria kết hợp squalane tạo màng dưỡng ẩm nhẹ, giữ nước suốt 12h và cho da mềm mại như cánh hoa.",
                390000L, 0, "Dưỡng ẩm", img(77), "ÉLAN", imgs(77)),

            new Product(8, "Kem Dưỡng Trắng SPF 30 Ban Ngày",
                "Kem dưỡng ẩm ban ngày tích hợp SPF 30 PA+++, làm sáng đều màu da và bảo vệ khỏi tia UV. Kết cấu nhẹ thấm nhanh, không nhờn dính.",
                620000L, 0, "Dưỡng ẩm", img(88), "ÉLAN", imgs(88)),

            new Product(9, "Gel Dưỡng Ẩm Tươi Mát Aloe Vera",
                "96% nha đam hữu cơ làm dịu da nhanh chóng, cấp ẩm tức thì và giảm đỏ kích ứng. Kết cấu gel trong suốt, cảm giác mát lạnh dễ chịu.",
                280000L, 0, "Dưỡng ẩm", img(99), "ÉLAN", imgs(99)),

            new Product(10, "Kem Dưỡng Ban Đêm Phục Hồi Chuyên Sâu",
                "Phức hợp ceramide và peptide phục hồi hàng rào bảo vệ da qua đêm, tái tạo tế bào và cải thiện kết cấu da.",
                720000L, 0, "Dưỡng ẩm", img(110), "ÉLAN", imgs(110)),

            new Product(11, "Kem Dưỡng Dịu Nhẹ Cho Da Nhạy Cảm",
                "Công thức không hương liệu, không paraben với panthenol và madecassoside giúp làm dịu da đỏ và bong tróc.",
                490000L, 0, "Dưỡng ẩm", img(121), "ÉLAN", imgs(121)),

            // Làm sạch
            new Product(12, "Sữa Rửa Mặt Tạo Bọt Gentle Foam",
                "pH cân bằng 5.5 với amino acid surfactant dịu nhẹ, làm sạch sâu mà không gây khô căng. Thêm chamomile và lô hội bảo vệ hàng rào ẩm da.",
                220000L, 0, "Làm sạch", img(132), "ÉLAN", imgs(132)),

            new Product(13, "Gel Rửa Mặt Salicylic Acid 2%",
                "BHA Salicylic Acid 2% thâm nhập sâu vào lỗ chân lông, loại bỏ bã nhờn và ngăn ngừa mụn đầu đen. Phù hợp da dầu mụn.",
                310000L, 0, "Làm sạch", img(143), "ÉLAN", imgs(143)),

            new Product(14, "Dầu Tẩy Trang Cleansing Balm",
                "Balm dạng sáp chuyển thành dầu mịn khi tiếp xúc da, hòa tan hoàn toàn kem chống nắng và lớp trang điểm dày.",
                380000L, 0, "Làm sạch", img(154), "ÉLAN", imgs(154)),

            new Product(15, "Nước Tẩy Trang Micellar 3-in-1",
                "Micelle tương tác như nam châm hút sạch bụi bẩn, dầu thừa và lớp trang điểm nhẹ mà không cần xả.",
                180000L, 0, "Làm sạch", img(165), "ÉLAN", imgs(165)),

            new Product(16, "Sữa Rửa Mặt Kiểm Soát Dầu Thừa",
                "Kaolin clay và zinc PCA hấp thu dầu thừa, kiểm soát bóng nhờn từ 8 tiếng mà không làm da mất ẩm.",
                260000L, 0, "Làm sạch", img(176), "ÉLAN", imgs(176)),

            // Toner
            new Product(17, "Toner Rose Water Cân Bằng Da",
                "Nước hoa hồng thuần chay giúp se khít lỗ chân lông, cân bằng độ ẩm và làm dịu da đỏ sau rửa mặt.",
                310000L, 0, "Toner", img(187), "ÉLAN", imgs(187)),

            new Product(18, "Toner BHA 2% Làm Sáng Đều Màu",
                "BHA 2% kết hợp niacinamide 5% tẩy tế bào chết hóa học nhẹ nhàng, làm đều màu da và thu nhỏ lỗ chân lông.",
                450000L, 0, "Toner", img(198), "ÉLAN", imgs(198)),

            new Product(19, "Toner Cấp Ẩm Ferment Essence",
                "Fermented galactomyces filtrate 97% kết hợp hyaluronic acid tạo lớp ẩm đầu tiên, giúp các bước skincare tiếp theo thẩm thấu tốt hơn.",
                360000L, 0, "Toner", img(209), "ÉLAN", imgs(209)),

            new Product(20, "Toner Thu Nhỏ Lỗ Chân Lông Witch Hazel",
                "Witch hazel tự nhiên kết hợp tea tree oil se khít lỗ chân lông tức thì và kháng khuẩn nhẹ.",
                290000L, 0, "Toner", img(220), "ÉLAN", imgs(220)),

            // Chống nắng
            new Product(21, "Kem Chống Nắng SPF 50+ PA++++",
                "Bảo vệ toàn diện khỏi tia UVA/UVB với SPF 50+ PA++++. Công thức không nhờn với niacinamide dưỡng sáng da.",
                540000L, 0, "Chống nắng", img(231), "ÉLAN", imgs(231)),

            new Product(22, "Xịt Chống Nắng Tái Bôi SPF 50",
                "Dạng xịt mịn tiện lợi để tái bôi chống nắng giữa ngày mà không làm hỏng lớp trang điểm. Kháng nước 4 tiếng.",
                420000L, 0, "Chống nắng", img(242), "ÉLAN", imgs(242)),

            new Product(23, "Cushion Chống Nắng Có Màu SPF 45",
                "Cushion 2-in-1 vừa chống nắng SPF 45 vừa che phủ nhẹ với finish tự nhiên căng bóng.",
                680000L, 0, "Chống nắng", img(253), "ÉLAN", imgs(253)),

            new Product(24, "Gel Chống Nắng Nhẹ Không Nhờn",
                "Kết cấu gel trong suốt 0% dầu, tan ngay khi chạm da không để lại vệt trắng. Lý tưởng cho da dầu vùng nhiệt đới.",
                380000L, 0, "Chống nắng", img(264), "ÉLAN", imgs(264)),

            // Mặt nạ
            new Product(25, "Mặt Nạ Ngủ Cấp Ẩm Chuyên Sâu",
                "Sleeping mask đậm đặc với hyaluronic acid và ceramide phục hồi da qua đêm. Dùng 2-3 lần mỗi tuần.",
                270000L, 0, "Mặt nạ", img(275), "ÉLAN", imgs(275)),

            new Product(26, "Mặt Nạ Đất Sét Làm Sạch Sâu Lỗ Chân Lông",
                "Kaolin và bentonite clay hút sạch bã nhờn và tạp chất trong lỗ chân lông, cân bằng dầu thừa.",
                320000L, 0, "Mặt nạ", img(286), "ÉLAN", imgs(286)),

            new Product(27, "Mặt Nạ Vải Dưỡng Trắng Gold Extract",
                "Sheet mask chứa chiết xuất vàng 24K và vitamin C dưỡng sáng tức thì. Tương đương 5 bước skincare sau 20 phút.",
                250000L, 0, "Mặt nạ", img(297), "ÉLAN", imgs(297)),

            new Product(28, "Mặt Nạ Peeling Enzyme Làm Sáng Da",
                "Enzyme papain và bromelain tẩy tế bào chết sinh học siêu dịu, phù hợp cả da nhạy cảm.",
                340000L, 0, "Mặt nạ", img(308), "ÉLAN", imgs(308)),

            // Mắt & Môi
            new Product(29, "Kem Mắt Peptide Chống Thâm Quầng",
                "Phức hợp peptide và caffeine giảm thâm quầng, bọng mắt và mờ nếp nhăn vùng mắt.",
                560000L, 0, "Mắt & Môi", img(319), "ÉLAN", imgs(319)),

            new Product(30, "Serum Môi Dưỡng Ẩm Vitamin E",
                "Vitamin E và shea butter cấp ẩm sâu, mềm môi và làm mờ đường chỉ môi. Mùi hương vanilla nhẹ tự nhiên.",
                290000L, 0, "Mắt & Môi", img(330), "ÉLAN", imgs(330))
        );
    }
}
