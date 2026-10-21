package com.shop.inventory.entity;

/**
 * Life cycle of a stock reservation: {@code RESERVED} then {@code COMMITTED} (order paid) or
 * {@code RELEASED} (order cancelled). {@code FAILED} reservations hold no stock.
 */
public enum ReservationStatus {
    RESERVED,
    FAILED,
    COMMITTED,
    RELEASED
}
