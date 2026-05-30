package com.jaroso.proyectosensores.services;

import com.jaroso.proyectosensores.dto.CamaraEventsResponseDto;
import com.jaroso.proyectosensores.dto.CamaraHealthDto;
import com.jaroso.proyectosensores.dto.CamaraLatestDto;
import com.jaroso.proyectosensores.dto.CamaraStatsResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CamaraService {

    @Value("${camara.url}")
    private String camaraUrl;

    @Autowired
    private RestTemplate restTemplate;

    public CamaraLatestDto getLatest(){
        return restTemplate.getForObject(camaraUrl + "/latest", CamaraLatestDto.class);
    }

    public CamaraHealthDto getHealth(){
        return restTemplate.getForObject(camaraUrl + "/health", CamaraHealthDto.class);
    }

    public CamaraEventsResponseDto getEvents(int limit, String dateFrom, String dateTo) {
        String url = camaraUrl + "/events?limit=" + limit;
        if (dateFrom != null) url += "&date_from=" + dateFrom;
        if (dateTo   != null) url += "&date_to="   + dateTo;
        return restTemplate.getForObject(url, CamaraEventsResponseDto.class);
    }

    public CamaraStatsResponseDto getStats(int limit, String dateFrom, String dateTo) {
        String url = camaraUrl + "/stats?limit=" + limit;
        if (dateFrom != null) url += "&date_from=" + dateFrom;
        if (dateTo   != null) url += "&date_to="   + dateTo;
        return restTemplate.getForObject(url, CamaraStatsResponseDto.class);
    }

}
