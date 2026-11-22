package com.realestates.backend.search.infrastructure;

import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ConTroTimKiem;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Bản tối thiểu cho chế độ in-memory (chỉ để ngữ cảnh Spring khởi động được): không có dữ liệu tìm kiếm. */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTimKiemRepository implements TimKiemRepository {

    @Override
    public List<DongTin> docTaiTro(BoLocTimKiem boLoc, int toiDa) {
        return List.of();
    }

    @Override
    public List<DongTin> docTuNhien(
            BoLocTimKiem boLoc,
            SapXepTin sapXep,
            ConTroTimKiem sau,
            int toiDaTaiTro,
            int gioiHan
    ) {
        return List.of();
    }
}
