package com.nus.team3.model;

import com.nus.team3.dao.TransactionDao;
import com.nus.team3.dto.Order;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.nus.team3.dao.TransactionDao.rootMapperPath;
import static com.nus.team3.dao.TransactionDao.selectAllUnmatchedQuery;

@Component
public class MasterPool {

    private Map<String, StockOrderPool> stockMap = new HashMap<>();
    private Map<String, String> transactionMap = new HashMap<>();
    private boolean isInit = false;
    private static final Logger logger = LoggerFactory.getLogger(MasterPool.class);
    private Thread thread;

    @Autowired
    @Qualifier("mysqlSqlSessionTemplate")
    private SqlSessionTemplate sqlSessionTemplate;

    public MasterPool() {
    }

    public void startUp(){
        this.isInit = true;
        List<Order> unmatchedOrdersInDatabase = sqlSessionTemplate.selectList(rootMapperPath + selectAllUnmatchedQuery);
        for (Order o: unmatchedOrdersInDatabase){
            addOrder(o);
        }
    }

    public void reSyncStockPool() {
        try {
            while (true) {
                List<Order> unmatchedOrdersInDatabase = sqlSessionTemplate.selectList(rootMapperPath + selectAllUnmatchedQuery);
                stockMap = new HashMap<>();
                for (Order o : unmatchedOrdersInDatabase) {
                    addOrder(o);
                }
                logger.info("Latest stock pool is updated.");
                TimeUnit.SECONDS.sleep(5);
            }
        }catch(Exception e) {}
    }

    public void addOrder(Order o){
        if(!isInit){
            startUp();
            this.thread = new Thread(){
                public void run(){
                    reSyncStockPool();
                }
            };
            thread.start();
        }
        if (!stockMap.keySet().contains(o.getStockTicker())){
            stockMap.put(o.getStockTicker(), new StockOrderPool(o.getStockTicker()));
        }
        stockMap.get(o.getStockTicker()).addByPriceByTimestamp(o);
        transactionMap.put(o.getTransactionId(), o.getStockTicker());
    }

    public String cancelOrder(String transactionId){
        if (!transactionMap.containsKey(transactionId)){
            return String.format("TransactionId %s not found in queue." , transactionId);
        }
        return stockMap.get(transactionMap.get(transactionId)).cancelOrder(transactionId);
    }

    public void match(Order o){
        stockMap.get(o.getStockTicker()).match();
    }

    public void showAllQueue(){
        for (StockOrderPool stockOrderPool: stockMap.values()){
            stockOrderPool.print();
        }
    }

    public void setInit(boolean init) {
        isInit = init;
    }

    public Map<String, StockOrderPool> getStockMap() {
        return stockMap;
    }

}
