package gov.openpto.odp.service;

import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.TrademarkDetail;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.dto.TrademarkSummary;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.TrademarkMapper;
import gov.openpto.odp.model.Trademark;
import gov.openpto.odp.repository.TrademarkRepository;
import gov.openpto.odp.repository.spec.SearchSort;
import gov.openpto.odp.repository.spec.TrademarkSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrademarkService {

    private final TrademarkRepository trademarks;
    private final TrademarkMapper mapper;

    public PageResponse<TrademarkSummary> search(TrademarkFilter filter, PageParams paging) {
        SearchSort sort = TrademarkSpecifications.sort(paging.sort(), filter);
        Page<Trademark> page = trademarks.findAll(
                TrademarkSpecifications.matchingOrdered(filter, sort),
                PageRequest.of(paging.pageOrDefault(), paging.sizeOrDefault()));
        return PageResponse.of(page, mapper::toSummary);
    }

    public TrademarkDetail detail(String serialNumber) {
        String key = serialNumber.trim();
        return trademarks.findBySerialNumber(key)
                .map(mapper::toDetail)
                .orElseThrow(() -> NotFoundException.of("Trademark", key));
    }
}
