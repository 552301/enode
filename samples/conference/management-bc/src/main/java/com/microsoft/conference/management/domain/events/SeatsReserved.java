package com.microsoft.conference.management.domain.events;

import com.microsoft.conference.management.domain.models.ReservationItem;
import com.microsoft.conference.management.domain.models.SeatAvailableQuantity;
import org.enodeframework.eventing.DomainEvent;

import java.util.List;

public class SeatsReserved extends DomainEvent<String> {
    public String reservationId;
    public List<ReservationItem> reservationItems;
    public List<SeatAvailableQuantity> seatAvailableQuantities;

    public SeatsReserved() {
    }

    public SeatsReserved(String reservationId, List<ReservationItem> reservationItems, List<SeatAvailableQuantity> seatAvailableQuantities) {
        this.reservationId = reservationId;
        this.reservationItems = reservationItems;
        this.seatAvailableQuantities = seatAvailableQuantities;
    }
}