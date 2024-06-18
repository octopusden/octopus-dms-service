var webpack = require('webpack');
var path = require('path');

const CleanWebpackPlugin = require('clean-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const HtmlWebpackPluginConfig = new HtmlWebpackPlugin({
  template : 'index.html',
  filename : 'index.html',
  inject : 'body',

});
const outputSubDir = 'src/main/resources/public'

var config = {
  context: __dirname + '/src/frontend',
  entry: './index.js',
  output: {
    path: `${__dirname}/${outputSubDir}`,
    filename: 'bundle.js'
  },
  module: {
    rules: [
      {
        test: /\.js?$/,
        enforce: "pre",
        use: ["source-map-loader"],
      },
      {
        test: /\.jsx?$/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              compact: false,
            }
          }
        ],
      },
      {
        test: /\.css$/,
        use: [
          {
            loader: MiniCssExtractPlugin.loader,
          },
					"css-loader",
					"postcss-loader"
        ]
      },
      {
        test: /\.svg$/,
        loader: 'file-loader',
      },
      {
        test: /\.(ttf|eot|woff|woff2)$/,
        use: {
          loader: "file-loader",
          options: {
            name: "fonts/[name].[ext]",
          },
        },
      },
    ]
  },

  plugins: [
    new CleanWebpackPlugin([outputSubDir]),
    new MiniCssExtractPlugin({
      // Options similar to the same options in webpackOptions.output
      // both options are optional
      filename: "[name].css",
      chunkFilename: "[id].css"
    }),
    HtmlWebpackPluginConfig,
  ],
  devtool: "source-map",
};

module.exports = config;

