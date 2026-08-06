#include <string>
#include <vector>

std::string string_join(const std::vector<std::string>& values, const std::string& separator) {
    std::string result;
    for (std::size_t index = 0; index < values.size(); ++index) {
        if (index > 0) {
            result += separator;
        }
        result += values[index];
    }
    return result;
}

std::vector<std::string> string_split(const std::string& value, const std::string& delimiter) {
    std::vector<std::string> result;
    std::size_t start = 0;
    while (true) {
        const std::size_t end = value.find(delimiter, start);
        result.push_back(value.substr(start, end == std::string::npos ? end : end - start));
        if (end == std::string::npos) {
            return result;
        }
        start = end + delimiter.size();
    }
}

std::string string_repeat(const std::string& value, std::size_t count) {
    std::string result;
    result.reserve(value.size() * count);
    for (std::size_t index = 0; index < count; ++index) {
        result += value;
    }
    return result;
}
