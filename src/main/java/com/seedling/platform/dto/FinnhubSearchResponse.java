package com.seedling.platform.dto;

import java.util.List;

public class FinnhubSearchResponse {

    private int count;
    private List<StockSearchDto> result;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<StockSearchDto> getResult() {
        return result;
    }

    public void setResult(List<StockSearchDto> result) {
        this.result = result;
    }
}