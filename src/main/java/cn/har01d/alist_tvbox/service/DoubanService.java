package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.entity.Alias;
import cn.har01d.alist_tvbox.entity.AliasRepository;
import cn.har01d.alist_tvbox.entity.Meta;
import cn.har01d.alist_tvbox.entity.MetaRepository;
import cn.har01d.alist_tvbox.entity.Movie;
import cn.har01d.alist_tvbox.entity.MovieRepository;
import cn.har01d.alist_tvbox.tvbox.MovieDetail;
import cn.har01d.alist_tvbox.util.Constants;
import cn.har01d.alist_tvbox.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DoubanService {

    private final MetaRepository metaRepository;
    private final MovieRepository movieRepository;
    private final AliasRepository aliasRepository;

    private final RestTemplate restTemplate;


    public DoubanService(MetaRepository metaRepository, MovieRepository movieRepository, AliasRepository aliasRepository, RestTemplateBuilder builder) {
        this.metaRepository = metaRepository;
        this.movieRepository = movieRepository;
        this.aliasRepository = aliasRepository;
        this.restTemplate = builder
                .defaultHeader(HttpHeaders.ACCEPT, Constants.ACCEPT)
                .defaultHeader(HttpHeaders.USER_AGENT, Constants.USER_AGENT)
                .build();
    }

    public Movie getByPath(String path) {
        Meta meta = metaRepository.findByPath(path);
        if (meta != null) {
            return meta.getMovie();
        }
        return null;
    }

    public List<MovieDetail> getHotRank() {
        List<MovieDetail> list = new ArrayList<>();
        Map<String, Object> request = new HashMap<>();
        request.put("pageNum", 0);
        request.put("pageSize", 100);
        try {
            JsonNode response = restTemplate.postForObject("https://pbaccess.video.qq.com/trpc.videosearch.hot_rank.HotRankServantHttp/HotRankHttp", request, JsonNode.class);
            ArrayNode arrayNode = (ArrayNode) response.path("data").path("navItemList").path(0).path("hotRankResult").path("rankItemList");
            for (JsonNode node : arrayNode) {
                MovieDetail detail = new MovieDetail();
                detail.setVod_name(node.get("title").asText());
                detail.setVod_id("msearch:" + detail.getVod_name());
                detail.setVod_pic("https://avatars.githubusercontent.com/u/97389433?s=120&v=4");

                setDoubanInfo(detail);

                list.add(detail);
            }
        } catch (Exception e) {
            log.warn("", e);
        }

        return list;
    }

    private void setDoubanInfo(MovieDetail detail) {
        Movie movie = getByName(detail.getVod_name());
        if (movie != null) {
            detail.setVod_pic(movie.getCover());
        }
    }

    public Movie getByName(String name) {
        name = TextUtils.fixName(name);
        List<Movie> movies = movieRepository.getByName(name);
        if (movies != null && !movies.isEmpty()) {
            return movies.get(0);
        }

        Alias alias = aliasRepository.findById(name).orElse(null);
        if (alias != null) {
            log.debug("name: {} alias: {}", name, alias.getAlias());
            return alias.getMovie();
        }

        if (TextUtils.isEnglishChar(name.codePointAt(0)) && TextUtils.isChineseChar(name.codePointAt(1))) {
            name = name.substring(1);
            log.debug("search by name: {}", name);
            movies = movieRepository.getByName(name);
            if (movies != null && !movies.isEmpty()) {
                return movies.get(0);
            }

            alias = aliasRepository.findById(name).orElse(null);
            if (alias != null) {
                log.debug("name: {} alias: {}", name, alias.getAlias());
                return alias.getMovie();
            }
        }

        return null;
    }

}
