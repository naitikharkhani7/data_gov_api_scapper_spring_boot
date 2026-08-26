package com.datagov.scrapper.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorDto {
    private String name;
    private long count;
}
