import React, { Component } from 'react';
import { Switch, Route, Redirect } from 'react-router-dom';
import NotFound from '../../components/NotFound';
import routerData from '../../routerConfig';

class MainRoutes extends Component {
  /**
   * 渲染路由组件
   */
  renderNormalRoute = (item, index) => {
    if (sessionStorage.getItem('user')) {
      return item.component ? (
        <Route
          key={index}
          path={item.path}
          component={item.component}
          exact={item.exact}
        />
      ) : null;
    }
    return <Redirect to="/user/login" />;
  };

  render() {
    return (
      <Switch>
        {/* 渲染路由表 */}
        {routerData.map(this.renderNormalRoute)}

        {/* 未匹配到的路由重定向到 NotFound */}
        <Route component={NotFound} />
      </Switch>
    );
  }
}

export default MainRoutes;
