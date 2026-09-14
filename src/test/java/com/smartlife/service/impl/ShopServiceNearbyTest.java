package com.smartlife.service.impl;

import com.smartlife.dto.NearbyShopDTO;
import com.smartlife.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ShopServiceNearbyTest {

    private static final double CENTER_X = 118.71111;
    private static final double CENTER_Y = 32.20573;

    @Test
    void filtersByRadiusAndSortsByDistance() {
        ShopServiceImpl service = spy(new ShopServiceImpl());
        doReturn(Arrays.asList(
                shop(1L, 1L, CENTER_X + 0.010, CENTER_Y),
                shop(2L, 1L, CENTER_X, CENTER_Y),
                shop(3L, 1L, CENTER_X + 0.100, CENTER_Y)
        )).when(service).list();

        List<NearbyShopDTO> result = service.queryNearbyShops(CENTER_X, CENTER_Y, 5000, null, 50);

        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(result.get(0).getId(), result.get(1).getId()));
        assertEquals(0D, result.get(0).getDistance(), 0.001D);
        assertTrue(result.get(1).getDistance() > 900D);
        assertTrue(result.get(1).getDistance() < 1000D);
    }

    @Test
    void appliesOptionalTypeAndLimit() {
        ShopServiceImpl service = spy(new ShopServiceImpl());
        doReturn(Arrays.asList(
                shop(1L, 1L, CENTER_X, CENTER_Y),
                shop(2L, 2L, CENTER_X + 0.001, CENTER_Y),
                shop(3L, 2L, CENTER_X + 0.002, CENTER_Y)
        )).when(service).list();

        List<NearbyShopDTO> result = service.queryNearbyShops(CENTER_X, CENTER_Y, 5000, 2L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals(2L, result.get(0).getTypeId());
    }

    @Test
    void haversineReturnsDistanceInMeters() {
        double distance = ShopServiceImpl.haversineMeters(CENTER_X, CENTER_Y, CENTER_X, CENTER_Y + 0.001);
        assertTrue(distance > 110D && distance < 112D);
    }

    private Shop shop(long id, long typeId, double x, double y) {
        return new Shop().setId(id).setTypeId(typeId).setName("demo-" + id)
                .setX(x).setY(y).setScore(48).setAvgPrice(50L);
    }
}
