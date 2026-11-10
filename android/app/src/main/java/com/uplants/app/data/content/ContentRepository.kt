package com.uplants.app.data.content

import com.uplants.app.data.model.Category
import com.uplants.app.data.model.Post
import com.uplants.app.data.model.PostPage
import com.uplants.app.data.network.apiCall
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** content-service (port 8081). See services/content-service/README.md. */
interface ContentApi {
    @GET("api/content/categories")
    suspend fun categories(): List<Category>

    @GET("api/content/posts")
    suspend fun posts(
        @Query("q") query: String? = null,
        @Query("type") type: String? = null,
        @Query("category") categoryId: Int? = null,
        @Query("author") authorId: String? = null,
        @Query("tag") tag: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PostPage

    @GET("api/content/feed")
    suspend fun feed(@Query("limit") limit: Int, @Query("offset") offset: Int): PostPage

    @GET("api/content/posts/{id}")
    suspend fun post(@Path("id") id: String): Post

    @DELETE("api/content/posts/{id}")
    suspend fun deletePost(@Path("id") id: String)

}

/** `sort` values for [ContentApi.posts]. */
enum class PostSort(val apiValue: String) { RECENT("recent"), RATING("rating"), VIEWS("views"), INTERACTIONS("interactions") }

data class PostFilter(
    val query: String? = null,
    val type: String? = null,
    val categoryId: Int? = null,
    val authorId: String? = null,
    val sort: PostSort = PostSort.RECENT,
)

class ContentRepository(private val api: ContentApi) {

    private var cachedCategories: List<Category>? = null

    suspend fun categories(): Result<List<Category>> =
        cachedCategories?.let { Result.success(it) }
            ?: apiCall { api.categories() }.onSuccess { cachedCategories = it }

    suspend fun posts(filter: PostFilter, limit: Int, offset: Int): Result<PostPage> = apiCall {
        api.posts(
            query = filter.query?.takeIf { it.isNotBlank() },
            type = filter.type,
            categoryId = filter.categoryId,
            authorId = filter.authorId,
            sort = filter.sort.apiValue,
            limit = limit,
            offset = offset,
        )
    }

    /** Personalised feed; needs login. */
    suspend fun feed(limit: Int, offset: Int): Result<PostPage> = apiCall { api.feed(limit, offset) }

    suspend fun post(id: String): Result<Post> = apiCall { api.post(id) }

    suspend fun deletePost(id: String): Result<Unit> = apiCall { api.deletePost(id) }

}
