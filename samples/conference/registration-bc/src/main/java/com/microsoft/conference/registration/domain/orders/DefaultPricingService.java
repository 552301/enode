package com.microsoft.conference.registration.domain.orders;

import com.microsoft.conference.registration.domain.SeatQuantity;
import com.microsoft.conference.registration.domain.orders.Models.OrderLine;
import com.microsoft.conference.registration.domain.orders.Models.OrderTotal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DefaultPricingService implements IPricingService {
    @Override
    public OrderTotal calculateTotal(String conferenceId, List<SeatQuantity> seatQuantityList) {
        List<OrderLine> orderLines = new ArrayList<>();
        for (SeatQuantity seatQuantity : seatQuantityList) {
            orderLines.add(new OrderLine(seatQuantity, seatQuantity.seatType.unitPrice.multiply(BigDecimal.valueOf(seatQuantity.quantity))));
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine x : orderLines) {
            total = total.add(x.lineTotal);
        }
        return new OrderTotal(orderLines, total);
    }
}
