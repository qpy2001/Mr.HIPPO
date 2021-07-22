package com.hippo.fresh.search;

import com.hippo.fresh.exception.ProductNotExistException;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchProductService {
    static final String PRODUCT_INDEX = "productindex";
    ////    public List<IndexedObjectInformation> createProductIndexBulk(final List<SearchProduct> searchProducts);
////    public String createProductIndex(SearchProduct searchProduct);
//    public void createProductIndexBulk(final List<SearchProduct> products);
////    public void createProductIndex(final SearchProduct product);
////    public void findProductsByName(final String name);
////    public void findProductsByPrice(final Double upperBound, final Double lowerBound);
//    public List<SearchProduct> processSearch(final String query);
//    public List<String> fetchSuggestions(String query);
    private ElasticsearchOperations elasticsearchOperations;

    private SearchProductRepository searchProductRepository;

    @Autowired
    public SearchProductService(final ElasticsearchOperations elasticsearchOperations, final SearchProductRepository searchProductRepository) {
        super();
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchProductRepository = searchProductRepository;
    }

    public void createProductIndexBulk(final List<SearchProduct> products) {
        searchProductRepository.saveAll(products);
    }

    //fuzz搜索功能
    public List<SearchProduct> processSearch(int page, int pageNum, String productName,int sort, int order, int upperBound, int lowerBound) {
        // 1. Create query on multiple fields enabling fuzzy search
        Query searchQuery;
        //如果没有指定排序顺序，就按照权重排序
        if (sort == 0) {
//            //对商品名，商品详情， 商品id赋予不同的权值
            List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                              QueryBuilders.matchPhrasePrefixQuery("name",productName), ScoreFunctionBuilders.weightFactorFunction(50)));
//                            QueryBuilders.matchPhraseQuery("name", productName), ScoreFunctionBuilders.weightFactorFunction(50)));
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("detail", productName), ScoreFunctionBuilders.weightFactorFunction(5)));
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("categoryFirst", productName), ScoreFunctionBuilders.weightFactorFunction(10)));
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("categorySecond", productName), ScoreFunctionBuilders.weightFactorFunction(15)));

            //Combine
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
            filterFunctionBuilders.toArray(builders);
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(builders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .setMinScore(2);

            BoolQueryBuilder boolQueryBuilder =
                    QueryBuilders.boolQuery()
                            //商品状态匹配
                            .must(QueryBuilders.termQuery("status", 1))
                            //对商品名，商品详情， 商品id赋予不同的权值
//                            .must(QueryBuilders.matchPhraseQuery("name",productName).boost(4))
//                            .must(QueryBuilders.matchPhraseQuery("detail",productName).boost(2))
                            //价格区间匹配
                            .must(QueryBuilders.rangeQuery("price").from(lowerBound).to(upperBound));

            searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(functionScoreQueryBuilder)
                    .withFilter(boolQueryBuilder)
                    //排序方式匹配
                    .withSort(SortBuilders.fieldSort(sort == 1 ? "salesAmount" : "price").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                    //分页匹配
                    .withPageable(PageRequest.of(page, pageNum))
                    .build();
        } else {
            //否则按照给定顺序排序
            BoolQueryBuilder boolQueryBuilder =
                    QueryBuilders.boolQuery()
//                        .must(QueryBuilders.matchPhrasePrefixQuery(productName, "name"))
                            .must(QueryBuilders.termQuery("status", 1))
                            .must(QueryBuilders.matchPhrasePrefixQuery("name", productName))
//                            .must(QueryBuilders.multiMatchQuery(productName, "name", "detail","categoryFirst","categorySecond")
//                                    .fuzziness(Fuzziness.AUTO))
                            //价格区间匹配
                            .must(QueryBuilders.rangeQuery("price").from(lowerBound).to(upperBound));
            //排序
            switch (sort){
                case 1:searchQuery = new NativeSearchQueryBuilder()
                        .withFilter(boolQueryBuilder)
                        //排序方式匹配
                        //按照销量排序
                        .withSort(SortBuilders.fieldSort("salesAmount").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                        //分页匹配
                        .withPageable(PageRequest.of(page, pageNum))
                        .build();
                break;
                case 2:searchQuery = new NativeSearchQueryBuilder()
                        .withFilter(boolQueryBuilder)
                        //排序方式匹配
                        //按照价格排序
                        .withSort(SortBuilders.fieldSort("price").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                        //分页匹配
                        .withPageable(PageRequest.of(page, pageNum))
                        .build();
                break;
                case 3:searchQuery = new NativeSearchQueryBuilder()
                        .withFilter(boolQueryBuilder)
                        //排序方式匹配
                        //按照评分排序
                        .withSort(SortBuilders.fieldSort("score").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                        //分页匹配
                        .withPageable(PageRequest.of(page, pageNum))
                        .build();
                break;
                default:searchQuery = new NativeSearchQueryBuilder()
                        .withFilter(boolQueryBuilder)
                        //分页匹配
                        .withPageable(PageRequest.of(page, pageNum))
                        .build();
            }
        }

        // 2. Execute search
        SearchHits<SearchProduct> productHits =
                elasticsearchOperations
                        .search(searchQuery, SearchProduct.class,
                                IndexCoordinates.of(PRODUCT_INDEX));

        // 3. Map searchHits to product list
        List<SearchProduct> productMatches = new ArrayList<SearchProduct>();
        productHits.forEach(searchHit -> {
            productMatches.add(searchHit.getContent());
        });
        if (productMatches.size() == 0)
            throw new ProductNotExistException(null);
        return productMatches;
    }
}

    //搜索推荐function
//    public List<String> fetchSuggestions(String query) {
//        QueryBuilder queryBuilder = QueryBuilders
////                .wildcardQuery("name", query+"*");
//                //由通配符查询改为前缀查询
////                .prefixQuery()
//                .matchPhrasePrefixQuery("name",query);
//
//        Query searchQuery = new NativeSearchQueryBuilder()
//                .withFilter(queryBuilder)
//                //按照销售额降序
//                .withSort(SortBuilders.fieldSort("salesAmount").order(SortOrder.DESC))
//                .withPageable(PageRequest.of(0, 7))
//                .build();
//
//        SearchHits<SearchProduct> searchSuggestions =
//                elasticsearchOperations.search(searchQuery,
//                        SearchProduct.class,
//                        IndexCoordinates.of(PRODUCT_INDEX));
//
//        List<String> suggestions = new ArrayList<String>();
//
//        searchSuggestions.getSearchHits().forEach(searchHit->{
//            suggestions.add(searchHit.getContent().getName());
//        });
//        return suggestions;
//    }
